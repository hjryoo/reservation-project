package kr.hhplus.be.server;

import kr.hhplus.be.server.application.ProcessPaymentService;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase.ProcessPaymentCommand;
import kr.hhplus.be.server.domain.port.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessPaymentServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private ProcessPaymentService processPaymentService;

    @BeforeEach
    void setUp() {
        processPaymentService = new ProcessPaymentService(
                reservationRepository, userRepository, seatRepository, paymentGateway);
    }

    @Test
    @DisplayName("결제 처리 성공 - 유효한 예약과 충분한 잔액으로 결제할 수 있다")
    void processPayment_Success() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        int seatNumber = 1;

        Reservation reservation = mock(Reservation.class);
        when(reservation.isExpired()).thenReturn(false);
        when(reservation.getConcertId()).thenReturn(concertId);
        when(reservation.getSeatNumber()).thenReturn(seatNumber);

        Reservation confirmedReservation = mock(Reservation.class);
        when(reservation.confirmPayment()).thenReturn(confirmedReservation);

        User user = User.create("user123", 200000L);
        user.assignId(userId);

        Seat seat = mock(Seat.class);
        Seat soldSeat = mock(Seat.class);
        when(seat.markAsSold()).thenReturn(soldSeat);

        Payment payment = Payment.create(reservationId, userId, amount);
        Payment completedPayment = payment.complete();

        when(reservationRepository.findById(reservationId)).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(seatRepository.findByConcertIdAndSeatNumber(concertId, seatNumber)).thenReturn(seat);
        when(paymentGateway.processPayment(any(Payment.class))).thenReturn(completedPayment);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(result.getReservationId()).isEqualTo(reservationId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAmount()).isEqualTo(amount);

        verify(reservationRepository).save(confirmedReservation);
        verify(seatRepository).save(soldSeat);
        verify(paymentGateway).processPayment(any(Payment.class));
    }

    @Test
    @DisplayName("결제 처리 실패 - 잔액이 부족하면 예외가 발생한다")
    void processPayment_InsufficientBalance_ThrowsException() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;

        Reservation reservation = mock(Reservation.class);
        when(reservation.isExpired()).thenReturn(false);

        User user = User.create("user123", 50000L); // 잔액 부족
        user.assignId(userId);

        when(reservationRepository.findById(reservationId)).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When & Then
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);

        assertThatThrownBy(() -> processPaymentService.processPayment(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잔액이 부족합니다");

        verify(paymentGateway, never()).processPayment(any(Payment.class));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    @DisplayName("결제 처리 실패 - 만료된 예약으로는 결제할 수 없다")
    void processPayment_ExpiredReservation_ThrowsException() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;

        Reservation expiredReservation = mock(Reservation.class);
        when(expiredReservation.isExpired()).thenReturn(true);

        when(reservationRepository.findById(reservationId)).thenReturn(expiredReservation);

        // When & Then
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);

        assertThatThrownBy(() -> processPaymentService.processPayment(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("예약이 만료되었습니다");

        verify(userRepository, never()).findById(any());
        verify(paymentGateway, never()).processPayment(any(Payment.class));
    }

    @Test
    @DisplayName("결제 처리 실패 - 존재하지 않는 사용자로는 결제할 수 없다")
    void processPayment_UserNotFound_ThrowsException() {
        // Given
        Long reservationId = 1L;
        Long userId = 999L;
        Long amount = 150000L;

        Reservation reservation = mock(Reservation.class);
        when(reservation.isExpired()).thenReturn(false);

        when(reservationRepository.findById(reservationId)).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);

        assertThatThrownBy(() -> processPaymentService.processPayment(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(paymentGateway, never()).processPayment(any(Payment.class));
    }

    @Test
    @DisplayName("결제 처리 실패 - 결제 게이트웨이 오류 시 예외가 발생한다")
    void processPayment_PaymentGatewayFailure_ThrowsException() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;

        Reservation reservation = mock(Reservation.class);
        when(reservation.isExpired()).thenReturn(false);

        User user = User.create("user123", 200000L);
        user.assignId(userId);

        when(reservationRepository.findById(reservationId)).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentGateway.processPayment(any(Payment.class)))
                .thenThrow(new RuntimeException("결제 게이트웨이 오류"));

        // When & Then
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);

        assertThatThrownBy(() -> processPaymentService.processPayment(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("결제 게이트웨이 오류");

        verify(paymentGateway).processPayment(any(Payment.class));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(seatRepository, never()).save(any(Seat.class));
    }

    @Test
    @DisplayName("결제 처리 시 올바른 순서로 처리된다")
    void processPayment_CorrectOrder() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        int seatNumber = 1;

        Reservation reservation = mock(Reservation.class);
        when(reservation.isExpired()).thenReturn(false);
        when(reservation.getConcertId()).thenReturn(concertId);
        when(reservation.getSeatNumber()).thenReturn(seatNumber);

        Reservation confirmedReservation = mock(Reservation.class);
        when(reservation.confirmPayment()).thenReturn(confirmedReservation);

        User user = User.create("user123", 200000L);
        user.assignId(userId);

        Seat seat = mock(Seat.class);
        Seat soldSeat = mock(Seat.class);
        when(seat.markAsSold()).thenReturn(soldSeat);

        Payment payment = Payment.create(reservationId, userId, amount);
        Payment completedPayment = payment.complete();

        when(reservationRepository.findById(reservationId)).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(seatRepository.findByConcertIdAndSeatNumber(concertId, seatNumber)).thenReturn(seat);
        when(paymentGateway.processPayment(any(Payment.class))).thenReturn(completedPayment);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        processPaymentService.processPayment(command);

        // Then: 호출 순서 검증
        var inOrder = inOrder(reservationRepository, userRepository, paymentGateway, seatRepository);

        inOrder.verify(reservationRepository).findById(reservationId);
        inOrder.verify(userRepository).findById(userId);
        inOrder.verify(paymentGateway).processPayment(any(Payment.class));
        inOrder.verify(reservationRepository).save(confirmedReservation);
        inOrder.verify(seatRepository).findByConcertIdAndSeatNumber(concertId, seatNumber);
        inOrder.verify(seatRepository).save(soldSeat);
    }

    @Test
    @DisplayName("잔액이 정확히 일치할 때 결제가 성공한다")
    void processPayment_ExactBalanceMatch_Success() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 100000L; // 정확히 같은 금액
        Long concertId = 100L;
        int seatNumber = 1;

        Reservation reservation = mock(Reservation.class);
        when(reservation.isExpired()).thenReturn(false);
        when(reservation.getConcertId()).thenReturn(concertId);
        when(reservation.getSeatNumber()).thenReturn(seatNumber);

        Reservation confirmedReservation = mock(Reservation.class);
        when(reservation.confirmPayment()).thenReturn(confirmedReservation);

        User user = User.create("user123", 100000L); // 정확히 같은 잔액
        user.assignId(userId);

        Seat seat = mock(Seat.class);
        Seat soldSeat = mock(Seat.class);
        when(seat.markAsSold()).thenReturn(soldSeat);

        Payment payment = Payment.create(reservationId, userId, amount);
        Payment completedPayment = payment.complete();

        when(reservationRepository.findById(reservationId)).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(seatRepository.findByConcertIdAndSeatNumber(concertId, seatNumber)).thenReturn(seat);
        when(paymentGateway.processPayment(any(Payment.class))).thenReturn(completedPayment);

        // When & Then: 예외가 발생하지 않아야 함
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);

        assertThatCode(() -> processPaymentService.processPayment(command))
                .doesNotThrowAnyException();

        verify(paymentGateway).processPayment(any(Payment.class));
        verify(reservationRepository).save(confirmedReservation);
        verify(seatRepository).save(soldSeat);
    }

    @Test
    @DisplayName("예약 확정과 좌석 판매가 모두 성공한다")
    void processPayment_ReservationAndSeatUpdated_Success() {
        // Given
        Long reservationId = 1L;
        Long userId = 1L;
        Long amount = 150000L;
        Long concertId = 100L;
        int seatNumber = 1;

        Reservation reservation = mock(Reservation.class);
        when(reservation.isExpired()).thenReturn(false);
        when(reservation.getConcertId()).thenReturn(concertId);
        when(reservation.getSeatNumber()).thenReturn(seatNumber);

        Reservation confirmedReservation = mock(Reservation.class);
        when(reservation.confirmPayment()).thenReturn(confirmedReservation);

        User user = User.create("user123", 200000L);
        user.assignId(userId);

        Seat seat = mock(Seat.class);
        Seat soldSeat = mock(Seat.class);
        when(seat.markAsSold()).thenReturn(soldSeat);

        Payment payment = Payment.create(reservationId, userId, amount);
        Payment completedPayment = payment.complete();

        when(reservationRepository.findById(reservationId)).thenReturn(reservation);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(seatRepository.findByConcertIdAndSeatNumber(concertId, seatNumber)).thenReturn(seat);
        when(paymentGateway.processPayment(any(Payment.class))).thenReturn(completedPayment);

        // When
        ProcessPaymentCommand command = new ProcessPaymentCommand(reservationId, userId, amount);
        Payment result = processPaymentService.processPayment(command);

        // Then: 반환값 검증
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);

        // 예약 확정 검증
        verify(reservation).confirmPayment();
        verify(reservationRepository).save(confirmedReservation);

        // 좌석 판매 검증
        verify(seat).markAsSold();
        verify(seatRepository).save(soldSeat);
    }
}
