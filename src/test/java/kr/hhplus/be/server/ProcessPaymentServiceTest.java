package kr.hhplus.be.server;

import kr.hhplus.be.server.application.ProcessPaymentService;
import kr.hhplus.be.server.application.service.ConcurrencySeatReservationService;
import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.config.TestEventConfig;
import kr.hhplus.be.server.config.TestPaymentConfig;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import kr.hhplus.be.server.domain.port.out.PaymentGateway;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.domain.repository.PaymentRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.domain.repository.UserBalanceRepository;
import kr.hhplus.be.server.infrastructure.persistence.ConcertJpaRepository;
import kr.hhplus.be.server.infrastructure.persistence.PaymentJpaRepository;
import kr.hhplus.be.server.infrastructure.persistence.SeatReservationJpaRepository;
import kr.hhplus.be.server.infrastructure.persistence.UserBalanceJpaRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
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
@Import({TestPaymentConfig.class, TestEventConfig.class})
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
    private ConcertRepository concertRepository;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private SeatReservationJpaRepository seatReservationJpaRepository;

    @Autowired
    private UserBalanceJpaRepository userBalanceJpaRepository;

    @Autowired
    private ConcertJpaRepository concertJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    private Long testUserId;
    private Long reservationId;
    private Long concertId;
    private Integer seatNumber;
    private Long paymentAmount;

    @BeforeEach
    void setUp() {
        // ✅ 1. 모든 데이터 삭제 (순서 중요!)
        paymentJpaRepository.deleteAllInBatch();
        seatReservationJpaRepository.deleteAllInBatch();
        userBalanceJpaRepository.deleteAllInBatch();
        concertJpaRepository.deleteAllInBatch();

        // ✅ 2. 캐시 초기화
        cacheManager.getCacheNames().forEach(name ->
                cacheManager.getCache(name).clear()
        );

        // ✅ 3. 고유한 userId 생성 (테스트 격리)
        testUserId = System.currentTimeMillis();  // 타임스탬프 사용
        seatNumber = 1;
        paymentAmount = 50000L;

        // 4. Concert 생성
        Concert concert = Concert.create(
                "Taylor Swift Concert",
                "Taylor Swift",
                "Olympic Stadium",
                100,
                50000L
        );
        concert.openBooking();
        Concert savedConcert = concertRepository.save(concert);
        concertId = savedConcert.getId();

        // 5. 사용자 잔액 생성 (새로 생성)
        UserBalance userBalance = UserBalance.create(testUserId, 100000L);
        userBalanceRepository.save(userBalance);

        // 6. 임시 예약 생성
        LocalDateTime now = LocalDateTime.now();
        SeatReservation reservation = SeatReservation.createWithTimes(
                concertId, seatNumber, testUserId, paymentAmount,
                now, now.plusMinutes(10)
        );
        SeatReservation savedReservation = seatReservationRepository.save(reservation);
        reservationId = savedReservation.getId();

        System.out.println("\n=== 테스트 초기화 완료 ===");
        System.out.println("Concert ID: " + concertId);
        System.out.println("Reservation ID: " + reservationId);
        System.out.println("User ID: " + testUserId);  // 고유한 ID 출력
        System.out.println("초기 잔액: 100,000원\n");
    }

    @AfterEach
    void tearDown() {
        paymentJpaRepository.deleteAllInBatch();
        seatReservationJpaRepository.deleteAllInBatch();
        userBalanceJpaRepository.deleteAllInBatch();
        concertJpaRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("결제 처리 성공 - 유효한 예약과 충분한 잔액으로 결제할 수 있다")
    void processPayment_Success() {
        ProcessPaymentCommand command = new ProcessPaymentCommand(
                reservationId, testUserId, paymentAmount);

        Payment result = processPaymentService.processPayment(command);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(result.getUserId()).isEqualTo(testUserId);
        assertThat(result.getAmount()).isEqualTo(paymentAmount);

        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId)
                .orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(50000L);

        SeatReservation finalSeat = seatReservationRepository.findById(reservationId)
                .orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    @DisplayName("결제 처리 시 올바른 순서로 처리된다")
    void processPayment_CorrectOrder() {
        ProcessPaymentCommand command = new ProcessPaymentCommand(
                reservationId, testUserId, paymentAmount);

        Payment result = processPaymentService.processPayment(command);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);

        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId)
                .orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(50000L);

        SeatReservation finalSeat = seatReservationRepository.findById(reservationId)
                .orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.SOLD);

        Payment savedPayment = paymentRepository.findById(result.getId()).orElseThrow();
        assertThat(savedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("예약이 존재하지 않으면 결제 실패")
    void processPayment_ReservationNotFound() {
        Long invalidReservationId = 999L;
        ProcessPaymentCommand command = new ProcessPaymentCommand(
                invalidReservationId, testUserId, paymentAmount);

        Payment result = processPaymentService.processPayment(command);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("예약 정보를 찾을 수 없습니다");

        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId)
                .orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(100000L);  // 롤백되어 원래대로
    }

    @Test
    @DisplayName("잔액이 부족하면 결제 실패")
    void processPayment_InsufficientBalance() {
        // 잔액을 10,000원으로 줄임
        UserBalance currentBalance = userBalanceRepository.findByUserId(testUserId)
                .orElseThrow();
        UserBalance reducedBalance = currentBalance.deduct(90000L);
        userBalanceRepository.save(reducedBalance);

        ProcessPaymentCommand command = new ProcessPaymentCommand(
                reservationId, testUserId, paymentAmount);

        Payment result = processPaymentService.processPayment(command);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("잔액이 부족합니다");

        UserBalance finalBalance = userBalanceRepository.findByUserId(testUserId)
                .orElseThrow();
        assertThat(finalBalance.getBalance()).isEqualTo(10000L);

        SeatReservation finalSeat = seatReservationRepository.findById(reservationId)
                .orElseThrow();
        assertThat(finalSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("다른 사용자의 예약은 결제할 수 없다")
    void processPayment_UnauthorizedUser() {
        Long otherUserId = testUserId + 1000L;  // ✅ 다른 고유 ID
        UserBalance otherUserBalance = UserBalance.create(otherUserId, 100000L);
        userBalanceRepository.save(otherUserBalance);

        ProcessPaymentCommand command = new ProcessPaymentCommand(
                reservationId, otherUserId, paymentAmount);

        Payment result = processPaymentService.processPayment(command);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("결제할 수 없는 예약입니다");

        UserBalance originalUserBalance = userBalanceRepository.findByUserId(testUserId)
                .orElseThrow();
        assertThat(originalUserBalance.getBalance()).isEqualTo(100000L);

        UserBalance otherBalance = userBalanceRepository.findByUserId(otherUserId)
                .orElseThrow();
        assertThat(otherBalance.getBalance()).isEqualTo(100000L);
    }
}