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

/**
 * 좌석 예약 서비스 (분산락 + 캐싱 적용)
 */
@Slf4j
@Service("seatReservationService")
@RequiredArgsConstructor
public class SeatReservationService implements ReserveSeatUseCase {

    private final SeatReservationRepository seatReservationRepository;
    private final SeatReservationQueryService seatQueryService; // 캐시 무효화용

    /**
     * 좌석 임시 예약 (분산락 + 캐시 무효화)
     */
    @DistributedLock(
            key = "'seat:reservation:' + #concertId + ':' + #seatNumber",
            waitTime = 5L,
            leaseTime = 3L
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

        // 4. 저장
        SeatReservation reserved = seatReservationRepository.save(seat);

        // ⭐ 5. 캐시 무효화
        seatQueryService.evictSeatCache(concertId);

        log.info("좌석 예약 성공 - concertId: {}, seatNumber: {}", concertId, seatNumber);
        return reserved;
    }

    /**
     * 예약 확정 (분산락 + 캐시 무효화)
     */
    @DistributedLock(
            key = "'seat:confirmation:' + #concertId + ':' + #seatNumber",
            waitTime = 5L,
            leaseTime = 3L
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

        // 캐시 무효화
        seatQueryService.evictSeatCache(concertId);

        log.info("좌석 확정 성공 - concertId: {}, seatNumber: {}", concertId, seatNumber);
        return confirmed;
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