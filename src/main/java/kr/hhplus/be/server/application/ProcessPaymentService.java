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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 결제 처리 서비스 (분산락 적용)
 *
 * 분산락 적용 이유:
 * - 중복 결제 방지 (동일 예약에 대한 여러 결제 시도 차단)
 * - 예약 확정과 잔액 차감의 원자성 보장
 * - 분산 환경에서 결제 프로세스 일관성 유지
 */
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
            leaseTime = 10L
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

                // ⭐ 핵심 수정: update 메서드 사용 (새로운 INSERT 방지)
                return paymentRepository.update(processedPayment);
            } else {
                // 6. 결제 실패 시 잔액 롤백
                userBalanceService.chargeBalance(command.userId(), command.amount());

                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.update(failedPayment);
            }

        } catch (Exception e) {
            // 예외 발생 시 결제 실패 처리
            Payment failedPayment = Payment.create(command.reservationId(), command.userId(), command.amount());
            failedPayment = failedPayment.fail("결제 처리 중 오류 발생: " + e.getMessage());
            return paymentRepository.save(failedPayment);
        }
    }

    @DistributedLock(
            key = "'payment:idempotent:' + #idempotencyKey",
            waitTime = 10L,
            leaseTime = 10L
    )
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

                return paymentRepository.update(processedPayment);
            } else {
                // 결제 실패 시 롤백
                userBalanceService.chargeBalance(command.userId(), command.amount());
                Payment failedPayment = processedPayment.fail("결제 게이트웨이 처리 실패");
                return paymentRepository.update(failedPayment);
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