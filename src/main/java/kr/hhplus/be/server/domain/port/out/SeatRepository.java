package kr.hhplus.be.server.domain.port.out;

import kr.hhplus.be.server.domain.model.Seat;
import kr.hhplus.be.server.domain.model.SeatStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatRepository {
    Seat findByConcertIdAndSeatNumber(Long concertId, int seatNumber);
    Seat save(Seat seat);
    boolean existsByConcertIdAndSeatNumberAndStatus(Long concertId, int seatNumber, SeatStatus status);
    List<Seat> findByConcertIdAndDateTime(Long concertId, LocalDateTime dateTime);

    long countByStatusAndConcertIdAndDateTime(SeatStatus status, Long concertId, LocalDateTime dateTime);

    boolean existsByConcertIdAndSeatNumberAndStatus(Long concertId, int seatNumber, Seat.SeatStatus status);
}

