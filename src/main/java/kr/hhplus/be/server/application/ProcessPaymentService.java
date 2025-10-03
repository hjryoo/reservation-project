package kr.hhplus.be.server.application;

import kr.hhplus.be.server.application.service.ConcurrencySeatReservationService;
import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase;
import kr.hhplus.be.server.domain.port.out.*;
import kr.hhplus.be.server.domain.repository.PaymentRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service("processPaymentService")
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ConcurrencyUserBalanceService userBalanceService;
    private final ConcurrencySeatReservationService seatReservationService;
    private final SeatReservationRepository seatReservationRepository;

    public ProcessPaymentService(PaymentRepository paymentRepository,
                                 PaymentGateway paymentGateway,
                                 ConcurrencyUserBalanceService userBalanceService,
                                 ConcurrencySeatReservationService seatReservationService,
                                 SeatReservationRepository seatReservationRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.userBalanceService = userBalanceService;
        this.seatReservationService = seatReservationService;
        this.seatReservationRepository = seatReservationRepository;
    }

    @Override
    @Transactional
    public Payment processPayment(ProcessPaymentCommand command) {
        try {
            // 1. 예약 정보 검증 (비관적 락 사용)
            SeatReservation reservation = seatReservationRepository
                    .findById(command.reservationId())
                    .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));

            // 예약 상태 및 사용자 확인
            if (!reservation.canBeConfirmed(command.userId())) {
                throw new IllegalStateException("결제할 수 없는 예약입니다. (만료되었거나 다른 사용자의 예약)");
            }

            // 2. 잔액 차감 (조건부 UPDATE 사용 - 가장 안전)
            UserBalance updatedBalance = userBalanceService.deductBalanceWithConditionalUpdate(
                    command.userId(),
                    command.amount()
            );

            // 3. 결제 객체 생성 및 저장
            Payment payment = Payment.create(
                    command.reservationId(),
                    command.userId(),
                    command.amount()
            );
            Payment savedPayment = paymentRepository.save(payment);

            // 4. 외부 결제 게이트웨이 호출
            Payment processedPayment = paymentGateway.processPayment(savedPayment);

            // 5. 결제 성공 시 좌석 확정
            if (processedPayment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                SeatReservation confirmedSeat = seatReservationService.confirmSeatReservation(
                        reservation.getConcertId(),
                        reservation.getSeatNumber(),
                        command.userId()
                );

                // 결제 완료 상태로 업데이트
                Payment completedPayment = processedPayment.complete(processedPayment.getTransactionId());
                return paymentRepository.save(completedPayment);
            } else {
                // 6. 결제 실패 시 잔액 롤백 (충전으로 복구)
                userBalanceService.chargeBalance(command.userId(), command.amount());

                // 실패한 결제 정보 저장
                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.save(failedPayment);
            }

        } catch (Exception e) {
            // 예외 발생 시 결제 실패 처리
            Payment failedPayment = Payment.create(command.reservationId(), command.userId(), command.amount());
            failedPayment = failedPayment.fail("결제 처리 중 오류 발생: " + e.getMessage());
            return paymentRepository.save(failedPayment);
        }
    }

    /**
     * 멱등성을 보장하는 결제 처리 (중복 결제 방지)
     */
    @Transactional
    public Payment processPaymentIdempotent(ProcessPaymentCommand command, String idempotencyKey) {
        // 1. 멱등성 키로 기존 결제 확인
        Optional<Payment> existingPayment = paymentRepository.findByReservationIdAndIdempotencyKey(
                command.reservationId(),
                idempotencyKey
        );

        if (existingPayment.isPresent()) {
            return existingPayment.get(); // 이미 처리된 결제 반환
        }

        // 2. 새로운 결제 처리 (멱등성 키 포함)
        return processPaymentWithIdempotency(command, idempotencyKey);
    }

    private Payment processPaymentWithIdempotency(ProcessPaymentCommand command, String idempotencyKey) {
        try {
            // 1. 예약 정보 검증
            SeatReservation reservation = seatReservationRepository
                    .findById(command.reservationId())
                    .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));

            if (!reservation.canBeConfirmed(command.userId())) {
                throw new IllegalStateException("결제할 수 없는 예약입니다.");
            }

            // 2. 잔액 차감
            UserBalance updatedBalance = userBalanceService.deductBalanceWithConditionalUpdate(
                    command.userId(),
                    command.amount()
            );

            // 3. 멱등성 키를 포함한 결제 객체 생성
            Payment payment = Payment.createWithIdempotency(
                    command.reservationId(),
                    command.userId(),
                    command.amount(),
                    idempotencyKey
            );
            Payment savedPayment = paymentRepository.save(payment);

            // 4. 외부 결제 처리
            Payment processedPayment = paymentGateway.processPayment(savedPayment);

            // 5. 결제 성공 시 좌석 확정
            if (processedPayment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                seatReservationService.confirmSeatReservation(
                        reservation.getConcertId(),
                        reservation.getSeatNumber(),
                        command.userId()
                );

                Payment completedPayment = processedPayment.complete(processedPayment.getTransactionId());
                return paymentRepository.save(completedPayment);
            } else {
                // 결제 실패 시 롤백
                userBalanceService.chargeBalance(command.userId(), command.amount());
                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.save(failedPayment);
            }

        } catch (Exception e) {
            Payment failedPayment = Payment.createWithIdempotency(
                    command.reservationId(),
                    command.userId(),
                    command.amount(),
                    idempotencyKey
            );
            failedPayment = failedPayment.fail("결제 처리 중 오류 발생: " + e.getMessage());
            return paymentRepository.save(failedPayment);
        }
    }
}