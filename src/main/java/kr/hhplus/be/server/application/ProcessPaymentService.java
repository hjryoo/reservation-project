package kr.hhplus.be.server.application;

import kr.hhplus.be.server.application.event.ReservationEventPublisher;
import kr.hhplus.be.server.application.service.ConcurrencySeatReservationService;
import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.domain.event.ReservationCompletedEvent;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase;
import kr.hhplus.be.server.domain.port.out.*;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.domain.repository.PaymentRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.infrastructure.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 결제 처리 서비스 (분산락 + 롤백 실패 처리 개선)
 *
 * 핵심 개선 사항:
 * 1. Watch Dog 활성화 (leaseTime = -1)
 * 2. 롤백 실패 시 로그 기록 (향후 SAGA 패턴 적용 대비)
 */
@Slf4j
@Service("processPaymentService")
@RequiredArgsConstructor
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ConcurrencyUserBalanceService userBalanceService;
    private final ConcurrencySeatReservationService seatReservationService;
    private final SeatReservationRepository seatReservationRepository;
    private final ConcertRepository concertRepository;
    private final ReservationEventPublisher eventPublisher;

    @Override
    @DistributedLock(
            key = "'payment:process:' + #command.reservationId",
            waitTime = 10L,
            leaseTime = -1
    )
    @Transactional
    public Payment processPayment(ProcessPaymentCommand command) {
        boolean balanceDeducted = false;
        try {
            SeatReservation reservation = seatReservationRepository
                    .findById(command.reservationId())
                    .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));

            if (!reservation.canBeConfirmed(command.userId())) {
                throw new IllegalStateException("결제할 수 없는 예약입니다.");
            }

            userBalanceService.deductBalanceWithConditionalUpdate(
                    command.userId(),
                    command.amount()
            );
            balanceDeducted = true;

            Payment payment = Payment.create(
                    command.reservationId(),
                    command.userId(),
                    command.amount()
            );
            Payment savedPayment = paymentRepository.save(payment);

            Payment processedPayment = paymentGateway.processPayment(savedPayment);

            if (processedPayment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                seatReservationService.confirmSeatReservation(
                        reservation.getConcertId(),
                        reservation.getSeatNumber(),
                        command.userId()
                );

                Payment finalPayment = paymentRepository.save(processedPayment);

                publishReservationCompletedEvent(
                        reservation.getId(),
                        reservation.getConcertId(),
                        command.userId(),
                        reservation.getSeatNumber(),
                        command.amount(),
                        processedPayment.getTransactionId()
                );

                return finalPayment;
            } else {
                safeRollbackBalance(command.userId(), command.amount(), command.reservationId());
                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.save(failedPayment);
            }

        } catch (Exception e) {
            log.error("결제 처리 중 예외 발생", e);
            if (balanceDeducted) {
                safeRollbackBalance(command.userId(), command.amount(), command.reservationId());
            }
            Payment failedPayment = Payment.create(
                    command.reservationId(), command.userId(), command.amount());
            failedPayment = failedPayment.fail("결제 처리 중 오류: " + e.getMessage());
            return paymentRepository.save(failedPayment);
        }
    }

    private void publishReservationCompletedEvent(
            Long reservationId, Long concertId, Long userId, Integer seatNumber,
            Long amount, String transactionId) {

        try {
            Concert concert = concertRepository.findById(concertId)
                    .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다."));

            ReservationCompletedEvent event = new ReservationCompletedEvent(
                    reservationId,
                    concertId,
                    userId,
                    seatNumber,
                    amount,
                    concert.getTitle(),
                    transactionId
            );

            eventPublisher.publish(event);

        } catch (Exception e) {
            log.error("예약 완료 이벤트 발행 실패 - reservationId: {}", reservationId, e);
        }
    }

    private void safeRollbackBalance(Long userId, Long amount, Long reservationId) {
        try {
            userBalanceService.chargeBalance(userId, amount);
            log.info("잔액 롤백 성공");
        } catch (Exception e) {
            log.error("[CRITICAL] 잔액 롤백 실패 - userId: {}, amount: {}",
                    userId, amount, e);
        }
    }

    @DistributedLock(
            key = "'payment:idempotent:' + #idempotencyKey",
            waitTime = 10L,
            leaseTime = -1
    )
    @Transactional
    public Payment processPaymentIdempotent(ProcessPaymentCommand command, String idempotencyKey) {
        Optional<Payment> existingPayment = paymentRepository.findByReservationIdAndIdempotencyKey(
                command.reservationId(),
                idempotencyKey
        );

        if (existingPayment.isPresent()) {
            return existingPayment.get();
        }

        return processPaymentWithIdempotency(command, idempotencyKey);
    }

    private Payment processPaymentWithIdempotency(ProcessPaymentCommand command, String idempotencyKey) {
        try {
            SeatReservation reservation = seatReservationRepository
                    .findById(command.reservationId())
                    .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));

            if (!reservation.canBeConfirmed(command.userId())) {
                throw new IllegalStateException("결제할 수 없는 예약입니다.");
            }

            userBalanceService.deductBalanceWithConditionalUpdate(
                    command.userId(),
                    command.amount()
            );

            Payment payment = Payment.createWithIdempotency(
                    command.reservationId(),
                    command.userId(),
                    command.amount(),
                    idempotencyKey
            );
            Payment savedPayment = paymentRepository.save(payment);

            Payment processedPayment = paymentGateway.processPayment(savedPayment);

            if (processedPayment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                seatReservationService.confirmSeatReservation(
                        reservation.getConcertId(),
                        reservation.getSeatNumber(),
                        command.userId()
                );

                // ✅ 수정: complete() 호출 제거
                return paymentRepository.save(processedPayment);
            } else {
                safeRollbackBalance(command.userId(), command.amount(), command.reservationId());
                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.save(failedPayment);
            }

        } catch (Exception e) {
            log.error("멱등성 결제 처리 중 예외 발생", e);
            safeRollbackBalance(command.userId(), command.amount(), command.reservationId());
            Payment failedPayment = Payment.createWithIdempotency(
                    command.reservationId(),
                    command.userId(),
                    command.amount(),
                    idempotencyKey
            );
            failedPayment = failedPayment.fail("결제 처리 중 오류: " + e.getMessage());
            return paymentRepository.save(failedPayment);
        }
    }
}
