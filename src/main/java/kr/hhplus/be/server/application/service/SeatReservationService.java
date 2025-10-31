package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.port.in.ReserveSeatUseCase;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.infrastructure.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 좌석 예약 서비스 (분산락 + 캐싱 + 매진 랭킹 적용)
 */
@Slf4j
@Service("seatReservationService")
@RequiredArgsConstructor
public class SeatReservationService implements ReserveSeatUseCase {

    private final SeatReservationRepository seatReservationRepository;
    private final SeatReservationQueryService seatQueryService;
    private final ConcertRepository concertRepository;
    private final ConcertRankingService rankingService; // 신규

    /**
     * 좌석 임시 예약 (분산락 적용)
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

        // 4. DB 저장
        SeatReservation reserved = seatReservationRepository.save(seat);

        // 5. 트랜잭션 커밋 후 캐시 무효화
        registerCacheEvictionAfterCommit(concertId);

        log.info("좌석 예약 성공 - concertId: {}, seatNumber: {}", concertId, seatNumber);
        return reserved;
    }

    /**
     * 예약 확정 (분산락 + 매진 체크)
     */
    @DistributedLock(
            key = "'seat:confirmation:' + #concertId + ':' + #seatNumber",
            waitTime = 5L,
            leaseTime = -1
    )
    @Transactional
    public SeatReservation confirmReservation(Long concertId, Integer seatNumber, Long userId) {
        log.info("좌석 확정 시도 - concertId: {}, seatNumber: {}, userId: {}", concertId, seatNumber, userId);

        // 1. 좌석 확정
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumber(concertId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));

        if (!seat.canBeConfirmed(userId)) {
            throw new IllegalStateException("결제할 수 없는 예약입니다.");
        }

        seat.confirm();
        SeatReservation confirmed = seatReservationRepository.save(seat);

        // 2. DB에서 최신 Concert 조회 (Optimistic Lock 충돌 방지)
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다."));

        concert.decreaseAvailableSeats();
        Concert updatedConcert = concertRepository.save(concert);

        // 3. 매진 체크 및 랭킹 등록
        if (updatedConcert.isSoldOut()) {
            registerSoldOutRankingAfterCommit(updatedConcert);
        }

        // 4. 캐시 무효화
        registerCacheEvictionAfterCommit(concertId);

        log.info("좌석 확정 성공 - concertId: {}, seatNumber: {}", concertId, seatNumber);
        return confirmed;
    }

    private void registerCacheEvictionAfterCommit(Long concertId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                seatQueryService.evictSeatCache(concertId);
                            } catch (Exception e) {
                                log.error("캐시 무효화 실패 - concertId: {}", concertId, e);
                            }
                        }
                    }
            );
        }
    }

    /**
     * 트랜잭션 커밋 후 매진 랭킹 등록
     *
     * 주의: Concert 객체를 직렬화하여 전달 (Detached 상태 방지)
     */
    private void registerSoldOutRankingAfterCommit(Concert concert) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Concert의 필요한 정보만 추출 (Detached 문제 방지)
            final Long concertId = concert.getId();
            final String title = concert.getTitle();

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                // 트랜잭션 커밋 후 다시 조회하여 등록
                                Concert freshConcert = concertRepository.findById(concertId)
                                        .orElseThrow(() -> new IllegalStateException("콘서트를 찾을 수 없습니다."));

                                rankingService.registerSoldOutConcert(freshConcert);
                                log.info("매진 랭킹 등록 완료 - concertId: {}, title: {}", concertId, title);
                            } catch (Exception e) {
                                log.error("매진 랭킹 등록 실패 - concertId: {}", concertId, e);
                            }
                        }
                    }
            );
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