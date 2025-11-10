package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

@Service("concurrencySeatReservationService")
public class ConcurrencySeatReservationService {

    private final SeatReservationRepository seatReservationRepository;
    private final ConcertRepository concertRepository;
    private static final Logger log = LoggerFactory.getLogger(SeatExpirationService.class);

    public ConcurrencySeatReservationService(SeatReservationRepository seatReservationRepository, ConcertRepository concertRepository) {
        this.seatReservationRepository = seatReservationRepository;
        this.concertRepository = concertRepository;
    }

    /**
     * 전략 1: 조건부 UPDATE를 사용한 좌석 예약 (가장 안전)
     * Race Condition 완전 방지
     */
    @Transactional
    public SeatReservation reserveSeatWithConditionalUpdate(Long concertId, Integer seatNumber, Long userId) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        // 원자적 연산으로 좌석 예약 시도
        int updatedRows = seatReservationRepository.reserveSeatConditionally(concertId, seatNumber, userId, expiresAt);

        if (updatedRows == 0) {
            throw new IllegalStateException("해당 좌석은 이미 예약되었거나 존재하지 않습니다.");
        }

        // 예약 성공 후 결과 조회
        return seatReservationRepository.findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new IllegalStateException("예약된 좌석을 조회할 수 없습니다."));
    }

    /**
     * 전략 2: 비관적 락을 사용한 좌석 예약 (락 대기 발생 가능)
     * 높은 동시성에서는 성능 저하 가능
     */
    @Transactional
    public SeatReservation reserveSeatWithPessimisticLock(Long concertId, Integer seatNumber, Long userId) {
        // 비관적 락으로 좌석 조회
        Optional<SeatReservation> seatOpt = seatReservationRepository.findByConcertIdAndSeatNumberWithLock(concertId, seatNumber);

        if (seatOpt.isEmpty()) {
            throw new IllegalArgumentException("좌석을 찾을 수 없습니다.");
        }

        SeatReservation seat = seatOpt.get();

        // 예약 가능 여부 확인
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("이미 예약되었거나 판매된 좌석입니다.");
        }

        // 임시 예약 처리
        seat.reserveTemporarily(userId);

        return seatReservationRepository.save(seat);
    }

    /**
     * 좌석 예약 확정 (결제 완료 시)
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeatReservation confirmSeatReservation(Long concertId, Integer seatNumber, Long userId) {
        int updatedRows = seatReservationRepository.confirmSeatConditionally(concertId, seatNumber, userId);

        if (updatedRows == 0) {
            throw new IllegalStateException("확정할 수 있는 예약이 없습니다. (만료되었거나 다른 사용자의 예약)");
        }

        int decreasedRows = concertRepository.decreaseAvailableSeatsAtomically(concertId);

        if (decreasedRows == 0) {
            throw new IllegalStateException("좌석 감소 실패 - 이미 매진되었거나 존재하지 않는 콘서트");
        }

        registerSoldOutCheckAfterCommit(concertId);

        return seatReservationRepository.findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new IllegalStateException("확정된 좌석을 조회할 수 없습니다."));
    }
    private void registerSoldOutCheckAfterCommit(Long concertId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                log.info("트랜잭션 커밋 후 콘서트 매진 체크 - concertId: {}", concertId);
                            } catch (Exception e) {
                                log.error("매진 체크 실패 - concertId: {}", concertId, e);
                            }
                        }
                    }
            );
        }
    }
}