package kr.hhplus.be.server.application;

import kr.hhplus.be.server.application.service.ConcurrencySeatReservationService;
import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.domain.model.*;
import kr.hhplus.be.server.domain.port.in.ProcessPaymentUseCase;
import kr.hhplus.be.server.domain.port.out.*;
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

    @Override
    @DistributedLock(
            key = "'payment:process:' + #command.reservationId",
            waitTime = 10L,
            leaseTime = -1 // Watch Dog 활성화
    )
    @Transactional
    public Payment processPayment(ProcessPaymentCommand command) {
        try {
            // 1. 예약 정보 검증
            SeatReservation reservation = seatReservationRepository
                    .findById(command.reservationId())
                    .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));

            if (!reservation.canBeConfirmed(command.userId())) {
                throw new IllegalStateException("결제할 수 없는 예약입니다. (만료되었거나 다른 사용자의 예약)");
            }

            // 2. 잔액 차감
            UserBalance updatedBalance = userBalanceService.deductBalanceWithConditionalUpdate(
                    command.userId(),
                    command.amount()
            );

            // 3. 결제 객체 생성 및 저장 (PENDING 상태)
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

                Payment completedPayment = processedPayment.complete(processedPayment.getTransactionId());
                return paymentRepository.save(completedPayment);
            } else {
                // 6. 결제 실패 시 잔액 롤백 (안전한 예외 처리)
                safeRollbackBalance(command.userId(), command.amount(), command.reservationId());

                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.save(failedPayment);
            }

        } catch (Exception e) {
            log.error("결제 처리 중 예외 발생 - reservationId: {}, userId: {}, amount: {}",
                    command.reservationId(), command.userId(), command.amount(), e);

            // 예외 발생 시에도 잔액 롤백 시도
            safeRollbackBalance(command.userId(), command.amount(), command.reservationId());

            // 예외 발생 시 결제 실패 처리
            Payment failedPayment = Payment.create(command.reservationId(), command.userId(), command.amount());
            failedPayment = failedPayment.fail("결제 처리 중 오류 발생: " + e.getMessage());
            return paymentRepository.save(failedPayment);
        }
    }

    @DistributedLock(
            key = "'payment:idempotent:' + #idempotencyKey",
            waitTime = 10L,
            leaseTime = -1 // Watch Dog 활성화
    )
    @Transactional
    public Payment processPaymentIdempotent(ProcessPaymentCommand command, String idempotencyKey) {
        // 1. 멱등성 키로 기존 결제 확인
        Optional<Payment> existingPayment = paymentRepository.findByReservationIdAndIdempotencyKey(
                command.reservationId(),
                idempotencyKey
        );

        if (existingPayment.isPresent()) {
            return existingPayment.get();
        }

        // 2. 새로운 결제 처리
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
                safeRollbackBalance(command.userId(), command.amount(), command.reservationId());

                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.save(failedPayment);
            }

        } catch (Exception e) {
            log.error("멱등성 결제 처리 중 예외 발생 - reservationId: {}, idempotencyKey: {}",
                    command.reservationId(), idempotencyKey, e);

            safeRollbackBalance(command.userId(), command.amount(), command.reservationId());

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

    /**
     * 안전한 잔액 롤백 처리
     *
     * 롤백 실패 시:
     * 1. 에러 로그 기록 (수동 복구 또는 자동 재시도 대상)
     * 2. 향후 SAGA 패턴 적용 시 보상 트랜잭션으로 전환 예정
     *
     * 주의: 롤백 실패는 시스템 정합성에 치명적이므로 반드시 처리 필요
     */
    private void safeRollbackBalance(Long userId, Long amount, Long reservationId) {
        try {
            userBalanceService.chargeBalance(userId, amount);
            log.info("잔액 롤백 성공 - userId: {}, amount: {}, reservationId: {}",
                    userId, amount, reservationId);
        } catch (Exception rollbackException) {
            // 롤백 실패는 심각한 문제이므로 ERROR 레벨로 기록
            log.error("[CRITICAL] 잔액 롤백 실패 - 수동 처리 필요! " +
                            "userId: {}, amount: {}, reservationId: {}, " +
                            "사용자의 잔액이 {}원 부족한 상태입니다. " +
                            "DB 직접 수정 또는 고객센터 통해 처리 필요",
                    userId, amount, reservationId, amount, rollbackException);

        }
    }
}