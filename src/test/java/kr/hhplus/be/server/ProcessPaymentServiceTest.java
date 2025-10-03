package kr.hhplus.be.server;

import kr.hhplus.be.server.application.ProcessPaymentService;
import kr.hhplus.be.server.application.service.ConcurrencySeatReservationService;
import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import kr.hhplus.be.server.domain.port.out.*;
import kr.hhplus.be.server.domain.repository.PaymentRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private ConcurrencyUserBalanceService userBalanceService;

    @Mock
    private ConcurrencySeatReservationService seatReservationService;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    private ProcessPaymentService processPaymentService;

    @BeforeEach
    void setUp() {
        processPaymentService = new ProcessPaymentService(
                paymentRepository,
                paymentGateway,
                userBalanceService,
                seatReservationService,
                seatReservationRepository
        );
    }

    @Test
    @DisplayName("결제 처리 성공 - 유효한 예약과 충분한 잔액으로 결제할 수 있다")
    void processPayment_Success() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        Integer seatNumber = 1;

        // 예약 정보 Mock
        SeatReservation reservation = SeatReservation.createTemporaryReservation(concertId, seatNumber, userId, amount);
        reservation.assignId(reservationId);

        // 잔액 정보 Mock
        UserBalance userBalance = UserBalance.of(userId, 200000L, LocalDateTime.now(), 0L);

        // 결제 객체 Mock
        Payment pendingPayment = Payment.create(reservationId, userId, amount);
        pendingPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);

        Payment completedPayment = pendingPayment.complete("txn-123");

        // 확정된 좌석 Mock
        SeatReservation confirmedSeat = SeatReservation.createConfirmedReservation(concertId, seatNumber, userId, amount);

        // Mock 설정
        when(seatReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userBalanceService.deductBalanceWithConditionalUpdate(userId, amount)).thenReturn(userBalance);
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(pendingPayment)
                .thenReturn(completedPayment);
        when(paymentGateway.processPayment(any(Payment.class))).thenReturn(completedPayment);
        when(seatReservationService.confirmSeatReservation(concertId, seatNumber, userId)).thenReturn(confirmedSeat);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(result.getReservationId()).isEqualTo(reservationId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAmount()).isEqualTo(amount);

        verify(seatReservationRepository).findById(reservationId);
        verify(userBalanceService).deductBalanceWithConditionalUpdate(userId, amount);
        verify(paymentGateway).processPayment(any(Payment.class));
        verify(seatReservationService).confirmSeatReservation(concertId, seatNumber, userId);
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 처리 실패 - 잔액이 부족하면 실패한 결제를 반환한다")
    void processPayment_InsufficientBalance_ReturnsFailed() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        Integer seatNumber = 1;

        SeatReservation reservation = SeatReservation.createTemporaryReservation(concertId, seatNumber, userId, amount);
        reservation.assignId(reservationId);

        Payment failedPayment = Payment.create(reservationId, userId, amount);
        failedPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);
        failedPayment = failedPayment.fail("결제 처리 중 오류 발생: 잔액이 부족합니다.");

        // Mock 설정: 잔액 부족으로 예외 발생
        when(seatReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userBalanceService.deductBalanceWithConditionalUpdate(userId, amount))
                .thenThrow(new IllegalStateException("잔액이 부족합니다. 현재 잔액: 50000, 차감 요청: 150000"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("잔액이 부족합니다");

        verify(seatReservationRepository).findById(reservationId);
        verify(userBalanceService).deductBalanceWithConditionalUpdate(userId, amount);
        verify(paymentGateway, never()).processPayment(any(Payment.class));
        verify(seatReservationService, never()).confirmSeatReservation(any(), any(), any());
    }

    @Test
    @DisplayName("결제 처리 실패 - 만료된 예약으로는 결제할 수 없다")
    void processPayment_ExpiredReservation_ReturnsFailed() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        Integer seatNumber = 1;

        // 만료된 예약 생성
        SeatReservation expiredReservation = SeatReservation.createTemporaryReservation(concertId, seatNumber, userId, amount);
        expiredReservation.assignId(reservationId);
        expiredReservation.forceExpire(LocalDateTime.now().minusMinutes(10)); // 10분 전에 만료

        Payment failedPayment = Payment.create(reservationId, userId, amount);
        failedPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);
        failedPayment = failedPayment.fail("결제 처리 중 오류 발생: 결제할 수 없는 예약입니다.");

        when(seatReservationRepository.findById(reservationId)).thenReturn(Optional.of(expiredReservation));
        when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("결제할 수 없는 예약");

        verify(seatReservationRepository).findById(reservationId);
        verify(userBalanceService, never()).deductBalanceWithConditionalUpdate(any(), any());
        verify(paymentGateway, never()).processPayment(any(Payment.class));
    }

    @Test
    @DisplayName("결제 처리 실패 - 존재하지 않는 예약으로는 결제할 수 없다")
    void processPayment_ReservationNotFound_ReturnsFailed() {
        // Given
        Long reservationId = 999L;
        Long userId = 1L;
        Long amount = 150000L;

        Payment failedPayment = Payment.create(reservationId, userId, amount);
        failedPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);
        failedPayment = failedPayment.fail("결제 처리 중 오류 발생: 예약 정보를 찾을 수 없습니다.");

        when(seatReservationRepository.findById(reservationId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("예약 정보를 찾을 수 없습니다");

        verify(seatReservationRepository).findById(reservationId);
        verify(userBalanceService, never()).deductBalanceWithConditionalUpdate(any(), any());
        verify(paymentGateway, never()).processPayment(any(Payment.class));
    }

    @Test
    @DisplayName("결제 처리 실패 - 결제 게이트웨이 오류 시 롤백 처리된다")
    void processPayment_PaymentGatewayFailure_RollbackPerformed() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        Integer seatNumber = 1;

        SeatReservation reservation = SeatReservation.createTemporaryReservation(concertId, seatNumber, userId, amount);
        reservation.assignId(reservationId);

        UserBalance userBalance = UserBalance.of(userId, 200000L, LocalDateTime.now(), 0L);

        Payment pendingPayment = Payment.create(reservationId, userId, amount);
        pendingPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);

        Payment failedPayment = pendingPayment.fail("결제 처리 중 오류 발생: 결제 게이트웨이 오류");

        // Mock 설정: 게이트웨이에서 예외 발생
        when(seatReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userBalanceService.deductBalanceWithConditionalUpdate(userId, amount)).thenReturn(userBalance);
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(pendingPayment)
                .thenReturn(failedPayment);
        when(paymentGateway.processPayment(any(Payment.class)))
                .thenThrow(new RuntimeException("결제 게이트웨이 오류"));

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then: 실패한 결제가 반환되어야 함
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).contains("결제 게이트웨이 오류");

        verify(paymentGateway).processPayment(any(Payment.class));
        verify(seatReservationService, never()).confirmSeatReservation(any(), any(), any());
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 게이트웨이에서 실패 응답이 오면 롤백 처리된다")
    void processPayment_PaymentGatewayFailureResponse_RollbackPerformed() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        Integer seatNumber = 1;

        SeatReservation reservation = SeatReservation.createTemporaryReservation(concertId, seatNumber, userId, amount);
        reservation.assignId(reservationId);

        UserBalance userBalance = UserBalance.of(userId, 200000L, LocalDateTime.now(), 0L);

        Payment pendingPayment = Payment.create(reservationId, userId, amount);
        pendingPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);

        Payment failedPayment = pendingPayment.fail("게이트웨이 실패");

        // Mock 설정: 게이트웨이에서 실패 응답
        when(seatReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userBalanceService.deductBalanceWithConditionalUpdate(userId, amount)).thenReturn(userBalance);
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(pendingPayment)
                .thenReturn(failedPayment);
        when(paymentGateway.processPayment(any(Payment.class))).thenReturn(failedPayment);
        when(userBalanceService.chargeBalance(userId, amount)).thenReturn(userBalance);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("게이트웨이 실패");

        verify(paymentGateway).processPayment(any(Payment.class));
        verify(userBalanceService).chargeBalance(userId, amount); // 롤백 확인
        verify(seatReservationService, never()).confirmSeatReservation(any(), any(), any());
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 처리 시 올바른 순서로 처리된다")
    void processPayment_CorrectOrder() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        Integer seatNumber = 1;

        SeatReservation reservation = SeatReservation.createTemporaryReservation(concertId, seatNumber, userId, amount);
        reservation.assignId(reservationId);

        UserBalance userBalance = UserBalance.of(userId, 200000L, LocalDateTime.now(), 0L);

        Payment pendingPayment = Payment.create(reservationId, userId, amount);
        pendingPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);

        Payment completedPayment = pendingPayment.complete("txn-123");
        SeatReservation confirmedSeat = SeatReservation.createConfirmedReservation(concertId, seatNumber, userId, amount);

        when(seatReservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userBalanceService.deductBalanceWithConditionalUpdate(userId, amount)).thenReturn(userBalance);
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(pendingPayment)
                .thenReturn(completedPayment);
        when(paymentGateway.processPayment(any(Payment.class))).thenReturn(completedPayment);
        when(seatReservationService.confirmSeatReservation(concertId, seatNumber, userId)).thenReturn(confirmedSeat);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        processPaymentService.processPayment(command);

        // Then: 호출 순서 검증
        var inOrder = inOrder(seatReservationRepository, userBalanceService, paymentRepository, paymentGateway, seatReservationService);

        inOrder.verify(seatReservationRepository).findById(reservationId);
        inOrder.verify(userBalanceService).deductBalanceWithConditionalUpdate(userId, amount);
        inOrder.verify(paymentRepository).save(any(Payment.class));
        inOrder.verify(paymentGateway).processPayment(any(Payment.class));
        inOrder.verify(seatReservationService).confirmSeatReservation(concertId, seatNumber, userId);
        inOrder.verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("멱등성 키를 사용한 중복 결제 방지 테스트")
    void processPaymentIdempotent_DuplicateRequest_ReturnsExistingPayment() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        String idempotencyKey = "payment-key-123";

        Payment existingPayment = Payment.createWithIdempotency(reservationId, userId, amount, idempotencyKey);
        existingPayment.assignTechnicalFields(1L, LocalDateTime.now(), LocalDateTime.now(), 0L);

        when(paymentRepository.findByReservationIdAndIdempotencyKey(reservationId, idempotencyKey))
                .thenReturn(Optional.of(existingPayment));

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPaymentIdempotent(command, idempotencyKey);

        // Then
        assertThat(result).isEqualTo(existingPayment);

        // 새로운 결제 처리가 수행되지 않아야 함
        verify(seatReservationRepository, never()).findById(any());
        verify(userBalanceService, never()).deductBalanceWithConditionalUpdate(any(), any());
        verify(paymentGateway, never()).processPayment(any(Payment.class));
        verify(paymentRepository).findByReservationIdAndIdempotencyKey(reservationId, idempotencyKey);
    }
}
