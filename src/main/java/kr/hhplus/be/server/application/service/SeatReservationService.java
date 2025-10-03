package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.Reservation;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.port.in.ReserveSeatUseCase;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("seatReservationService")
@Transactional
public class SeatReservationService implements ReserveSeatUseCase {

    private final SeatReservationRepository seatReservationRepository;

    public SeatReservationService(SeatReservationRepository seatReservationRepository) {
        this.seatReservationRepository = seatReservationRepository;
    }

    /**
     * 좌석 상태 전환: 예약 가능 → 임시 예약
     */
    public SeatReservation reserveSeatTemporarily(Long concertId, Integer seatNumber, Long userId) {
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumberForUpdate(concertId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다: " + seatNumber));

        // 2. 예약 가능성 검증
        if (!seat.isAvailable()) {
            throw new IllegalStateException("이미 예약된 좌석입니다: " + seatNumber);
        }
        seat.reserveTemporarily(userId);

        return seatReservationRepository.save(seat);
    }


    /**
     * 좌석 상태 전환: 임시 예약 → 확정 예약
     */
    @Transactional
    public SeatReservation confirmReservation(Long concertId, Integer seatNumber, Long userId) {
        SeatReservation seat = seatReservationRepository
                .findByConcertIdAndSeatNumberForUpdate(concertId, seatNumber)
                .orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));

        if (!seat.canBeConfirmed(userId)) {
            throw new IllegalStateException("결제할 수 없는 예약입니다.");
        }

        seat.confirm();

        return seatReservationRepository.save(seat);
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