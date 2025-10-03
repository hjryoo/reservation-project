package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.*;
import kr.hhplus.be.server.config.TestPaymentConfig;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.out.PaymentGateway;
import kr.hhplus.be.server.domain.repository.*;
import kr.hhplus.be.server.domain.port.out.UserRepository;
import kr.hhplus.be.server.interfaces.web.UserController;
import kr.hhplus.be.server.interfaces.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestPaymentConfig.class)
@Transactional
public class ConcertReservationFlowIntegrationTest {

    @Autowired private UserController userController;
    @Autowired private SeatReservationService seatReservationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ConcertDateRepository concertDateRepository;
    @Autowired private SeatReservationRepository seatReservationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentGateway paymentGateway; // 이제 정상 주입됨

    private User testUser;
    private Concert testConcert;
    private ConcertDate testConcertDate;
    private Integer testSeatNumber;
    private Long seatPrice;

    @BeforeEach
    void setUp() {
        try {
            setupTestData();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 데이터 설정 실패", e);
        }
    }

    private void setupTestData() {
        // 사용자 생성
        CreateUserRequest createUserRequest = new CreateUserRequest("user123", 0L);
        UserBalanceResponse userResponse = userController.createUser(createUserRequest).getBody();
        testUser = userRepository.findById(userResponse.id()).orElseThrow();

        // 콘서트 생성
        testConcert = Concert.create(
                "테스트 콘서트",
                "아티스트",
                "서울 올림픽 경기장",
                50,
                100000L
        );
        testConcert = concertRepository.save(testConcert);

        // 콘서트 일정 생성
        LocalDateTime concertDateTime = LocalDateTime.now().plusDays(7);
        testConcertDate = ConcertDate.create(
                testConcert.getId(), // 이제 ID가 확실히 보장됨
                concertDateTime,
                concertDateTime.minusHours(1),
                concertDateTime.plusHours(3),
                50
        );
        testConcertDate.openBooking();
        testConcertDate = concertDateRepository.save(testConcertDate);

        testSeatNumber = 1;
        seatPrice = 100000L;

        // 예약 가능한 좌석 생성
        SeatReservation availableSeat = SeatReservation.createAvailableSeat(
                testConcert.getId(), testSeatNumber, seatPrice);
        seatReservationRepository.save(availableSeat);

        // 잔액 충전
        ChargeBalanceRequest chargeRequest = new ChargeBalanceRequest(150000L);
        userController.chargeBalance(testUser.getUserId(), chargeRequest);
    }

    @Test
    @DisplayName("기본 좌석 예약 흐름 테스트")
    void shouldReserveSeatSuccessfully() {
        // Given: 초기 상태 확인
        User initialUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(initialUser.getBalance()).isEqualTo(150000L);

        // When: 좌석 예약
        SeatReservation reservedSeat = seatReservationService.reserveSeatTemporarily(
                testConcert.getId(), testSeatNumber, testUser.getId());

        // Then: 예약 상태 검증
        assertThat(reservedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(reservedSeat.getUserId()).isEqualTo(testUser.getId());
        assertThat(reservedSeat.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Payment 생성 및 저장 테스트")
    void shouldCreateAndSavePayment() {
        // Given: 좌석 예약 완료
        SeatReservation reservedSeat = seatReservationService.reserveSeatTemporarily(
                testConcert.getId(), testSeatNumber, testUser.getId());

        // When: Payment 생성
        String idempotencyKey = "payment-test-" + UUID.randomUUID();
        Payment payment = Payment.createWithReservation(
                reservedSeat.getId(),
                testUser.getId(),
                seatPrice,
                "CREDIT_CARD",
                idempotencyKey
        );

        // Then: Payment 저장 및 검증
        Payment savedPayment = paymentRepository.save(payment);

        assertThat(savedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        assertThat(savedPayment.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(savedPayment.getAmount()).isEqualTo(seatPrice);
    }

    @Test
    @DisplayName("사용자 잔액 차감 테스트")
    void shouldDeductUserBalance() {
        // Given: 초기 잔액 확인
        User initialUser = userRepository.findById(testUser.getId()).orElseThrow();
        Long initialBalance = initialUser.getBalance();

        // When: 잔액 차감
        User updatedUser = initialUser.deductBalance(seatPrice);
        userRepository.save(updatedUser);

        // Then: 잔액 차감 확인
        User finalUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(finalUser.getBalance()).isEqualTo(initialBalance - seatPrice);
    }

    @Test
    @DisplayName("좌석 확정 예약 테스트")
    void shouldConfirmSeatReservation() {
        // Given: 임시 예약 생성
        SeatReservation reservedSeat = seatReservationService.reserveSeatTemporarily(
                testConcert.getId(), testSeatNumber, testUser.getId());

        // When: 예약 확정
        SeatReservation confirmedSeat = seatReservationService.confirmReservation(
                testConcert.getId(), testSeatNumber, testUser.getId());

        // Then: 확정 상태 검증
        assertThat(confirmedSeat.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(confirmedSeat.getUserId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("멱등성 키를 통한 중복 결제 방지 테스트")
    void shouldPreventDuplicatePaymentWithIdempotencyKey() {
        // Given: 좌석 예약 완료
        SeatReservation reservedSeat = seatReservationService.reserveSeatTemporarily(
                testConcert.getId(), testSeatNumber, testUser.getId());

        String idempotencyKey = "duplicate-test-key";

        // When: 첫 번째 결제 생성
        Payment firstPayment = Payment.createWithReservation(
                reservedSeat.getId(), testUser.getId(), seatPrice,
                "CREDIT_CARD", idempotencyKey);
        paymentRepository.save(firstPayment);

        assertThatThrownBy(() -> {
            Payment duplicatePayment = Payment.createWithReservation(
                    reservedSeat.getId(), testUser.getId(), seatPrice,
                    "CREDIT_CARD", idempotencyKey);
            paymentRepository.save(duplicatePayment);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("잔액 부족 시 차감 실패 테스트")
    void shouldFailWhenInsufficientBalance() {
        // Given: 잔액이 부족한 사용자
        CreateUserRequest poorUserRequest = new CreateUserRequest("poorUser", 50000L);
        UserBalanceResponse poorUserResponse = userController.createUser(poorUserRequest).getBody();
        User poorUser = userRepository.findById(poorUserResponse.id()).orElseThrow();

        // When & Then: 잔액 부족으로 차감 실패
        assertThatThrownBy(() -> poorUser.deductBalance(100000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잔액이 부족합니다");
    }
}