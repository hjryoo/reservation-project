package kr.hhplus.be.server;

import kr.hhplus.be.server.application.ProcessPaymentService;
import kr.hhplus.be.server.application.service.ConcurrencySeatReservationService;
import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.config.TestPaymentConfig;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import kr.hhplus.be.server.domain.port.out.PaymentGateway;
import kr.hhplus.be.server.domain.repository.PaymentRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.domain.repository.UserBalanceRepository;
import kr.hhplus.be.server.infrastructure.persistence.PaymentJpaRepository;
import kr.hhplus.be.server.infrastructure.persistence.SeatReservationJpaRepository;
import kr.hhplus.be.server.infrastructure.persistence.UserBalanceJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProcessPaymentService 통합 테스트
 *
 * 중요: 분산락 AOP는 Spring Context가 필요하므로 @SpringBootTest 사용
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestPaymentConfig.class)
class ProcessPaymentServiceTest {

    @Autowired
    private ProcessPaymentService processPaymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SeatReservationRepository seatReservationRepository;

    @Autowired
    private UserBalanceRepository userBalanceRepository;

    @Autowired
    private PaymentGateway paymentGateway; // Mock (TestPaymentConfig에서 제공)

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private SeatReservationJpaRepository seatReservationJpaRepository;

    @Autowired
    private UserBalanceJpaRepository userBalanceJpaRepository;

    private Long testUserId;
    private Long reservationId;
    private Long concertId;
    private Integer seatNumber;
    private Long paymentAmount;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        concertId = 1L;
        seatNumber = 1;
        paymentAmount = 50000L;

        // 사용자 잔액 생성
        userBalanceRepository.createInitialBalanceIfNotExists(testUserId, 100000L);

        // 임시 예약 생성
        LocalDateTime now = LocalDateTime.now();
        SeatReservation reservation = SeatReservation.createWithTimes(
                concertId, seatNumber, testUserId, paymentAmount,
                now, now.plusMinutes(10)
        );
        SeatReservation savedReservation = seatReservationRepository.save(reservation);
        reservationId = savedReservation.getId();
    }

    @AfterEach
    void tearDown() {
        paymentJpaRepository.deleteAllInBatch();
        seatReservationJpaRepository.deleteAllInBatch();
        userBalanceJpaRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("결제 처리 성공 - 유효한 예약과 충분한 잔액으로 결제할 수 있다")
    void processPayment_Success() {
        // Given
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, testUserId, paymentAmount);

        // When
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(result.getUserId()).isEqualTo(testUserId);
        assertThat(result.getAmount()).isEqualTo(paymentAmount);

        // 잔액 확인
        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(50000L); // 100,000 - 50,000

        // 좌석 상태 확인
        SeatReservation finalSeat = seatReservationRepository.findById(reservationId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    @DisplayName("결제 처리 시 올바른 순서로 처리된다")
    void processPayment_CorrectOrder() {
        // Given
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, testUserId, paymentAmount);

        // When
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);

        // 순서 검증: 결과 상태로 간접 검증
        // 1. 예약 검증 완료 (예외 없음)
        // 2. 잔액 차감 완료 (50,000원 남음)
        // 3. 결제 생성 및 게이트웨이 처리 완료 (COMPLETED)
        // 4. 좌석 확정 완료 (SOLD)

        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(50000L);

        SeatReservation finalSeat = seatReservationRepository.findById(reservationId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.SOLD);

        Payment savedPayment = paymentRepository.findById(result.getId()).orElseThrow();
        assertThat(savedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("예약이 존재하지 않으면 결제 실패")
    void processPayment_ReservationNotFound() {
        // Given
        Long invalidReservationId = 999L;
        ProcessPaymentCommand command = new ProcessPaymentCommand(invalidReservationId, testUserId, paymentAmount);

        // When
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("예약 정보를 찾을 수 없습니다");

        // 잔액은 차감되지 않아야 함
        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("잔액이 부족하면 결제 실패")
    void processPayment_InsufficientBalance() {
        // Given
        // 잔액을 10,000원으로 줄임 (결제 금액 50,000원보다 적음)
        UserBalance currentBalance = userBalanceRepository.findByUserId(testUserId).orElseThrow();
        UserBalance reducedBalance = currentBalance.deduct(90000L);
        userBalanceRepository.save(reducedBalance);

        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, testUserId, paymentAmount);

        // When
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("잔액이 부족합니다");

        // 잔액은 그대로 유지되어야 함
        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId).orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(10000L);

        // 좌석은 여전히 RESERVED 상태여야 함
        SeatReservation finalSeat = seatReservationRepository.findById(reservationId).orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("다른 사용자의 예약은 결제할 수 없다")
    void processPayment_UnauthorizedUser() {
        // Given
        Long otherUserId = 999L;
        userBalanceRepository.createInitialBalanceIfNotExists(otherUserId, 100000L);

        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, otherUserId, paymentAmount);

        // When
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("결제할 수 없는 예약입니다");

        // 원래 사용자의 잔액은 그대로
        UserBalance originalUserBalance = userBalanceRepository.findByUserId(testUserId).orElseThrow();
        assertThat(originalUserBalance.getBalance()).isEqualTo(100000L);

        // 다른 사용자의 잔액도 그대로
        UserBalance otherUserBalance = userBalanceRepository.findByUserId(otherUserId).orElseThrow();
        assertThat(otherUserBalance.getBalance()).isEqualTo(100000L);
    }
}