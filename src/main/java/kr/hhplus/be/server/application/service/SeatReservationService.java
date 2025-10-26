package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.port.in.ReserveSeatUseCase;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.infrastructure.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 좌석 예약 서비스 (분산락 + 캐싱 적용)
 *
 * 핵심 개선 사항:
 * 1. 캐시 무효화를 트랜잭션 커밋 후로 이동 (데이터 정합성 보장)
 * 2. 분산락 범위 최소화 (네트워크 I/O 제외)
 */
@Slf4j
@Service("seatReservationService")
@RequiredArgsConstructor
public class SeatReservationService implements ReserveSeatUseCase {

    private final SeatReservationRepository seatReservationRepository;
    private final SeatReservationQueryService seatQueryService;

    /**
     * 좌석 임시 예약 (분산락 적용)
     *
     * 분산락 범위: DB 조회 → 상태 검증 → 예약 처리 → DB 저장
     * 캐시 무효화: 트랜잭션 커밋 후 실행 (분산락 범위 밖)
     */
    @DistributedLock(
            key = "'seat:reservation:' + #concertId + ':' + #seatNumber",
            waitTime = 5L,
            leaseTime = -1 // Watch Dog 활성화
    )
    @Transactional
    public SeatReservation reserveSeatTemporarily(Long concertId, Integer seatNumber, Long userId) {
        log.info("좌석 예약 시도 - concertId: {}, seatNumber: {}, userId: {}", concertId, seatNumber, userId);

        // 1. 좌석 조회
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다: " + seatNumber));

        // 2. 예약 가능성 검증
        if (!seat.isAvailable()) {
            throw new IllegalStateException("이미 예약된 좌석입니다: " + seatNumber);
        }

        // 3. 임시 예약 처리
        seat.reserveTemporarily(userId);

        // 4. DB 저장 (분산락 범위 내)
        SeatReservation reserved = seatReservationRepository.save(seat);

        // 5. 트랜잭션 커밋 후 캐시 무효화 (분산락 범위 밖)
        // 데이터 정합성 보장: DB 커밋 완료 → 캐시 삭제 순서
        registerCacheEvictionAfterCommit(concertId);

        log.info("좌석 예약 성공 - concertId: {}, seatNumber: {}", concertId, seatNumber);
        return reserved;
    }

    /**
     * 예약 확정 (분산락 적용)
     */
    @DistributedLock(
            key = "'seat:confirmation:' + #concertId + ':' + #seatNumber",
            waitTime = 5L,
            leaseTime = -1 // Watch Dog 활성화
    )
    @Transactional
    public SeatReservation confirmReservation(Long concertId, Integer seatNumber, Long userId) {
        log.info("좌석 확정 시도 - concertId: {}, seatNumber: {}, userId: {}", concertId, seatNumber, userId);

        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));

        if (!seat.canBeConfirmed(userId)) {
            throw new IllegalStateException("결제할 수 없는 예약입니다.");
        }

        seat.confirm();
        SeatReservation confirmed = seatReservationRepository.save(seat);

        // 트랜잭션 커밋 후 캐시 무효화
        registerCacheEvictionAfterCommit(concertId);

        log.info("좌석 확정 성공 - concertId: {}, seatNumber: {}", concertId, seatNumber);
        return confirmed;
    }

    /**
     * 트랜잭션 커밋 후 캐시 무효화 등록
     *
     * 장점:
     * 1. DB 커밋 완료 후에만 캐시 삭제 (데이터 정합성 보장)
     * 2. 캐시 무효화 시간이 분산락 점유 시간에 포함되지 않음
     * 3. Redis 네트워크 지연이 다른 요청에 영향 없음
     */
    private void registerCacheEvictionAfterCommit(Long concertId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                seatQueryService.evictSeatCache(concertId);
                            } catch (Exception e) {
                                log.error("캐시 무효화 실패 - concertId: {}, 캐시 불일치 가능성 있음",
                                        concertId, e);
                            }
                        }
                    }
            );
        } else {
            // 트랜잭션이 활성화되지 않은 경우 즉시 실행
            log.warn("트랜잭션이 활성화되지 않음 - 즉시 캐시 무효화 실행: concertId={}", concertId);
            seatQueryService.evictSeatCache(concertId);
        }
    }

    @Override
    public Reservation reserve(ReserveSeatCommand command) {
        SeatReservation reservedSeat = reserveSeatTemporarily(
                command.concertId(),
                command.seatNumber(),
                command.userId()
        );

        return convertToReservation(reservedSeat);
    }

    private Reservation convertToReservation(SeatReservation seatReservation) {
        Reservation reservation = Reservation.create(
                seatReservation.getUserId(),
                seatReservation.getConcertId(),
                seatReservation.getSeatNumber(),
                seatReservation.getPrice()
        );
        reservation.assignId(seatReservation.getId());
        return reservation;
    }
}