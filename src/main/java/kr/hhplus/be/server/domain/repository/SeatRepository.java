package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.Seat;
import kr.hhplus.be.server.domain.model.SeatStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatRepository {
    List<Seat> findSeatsByConcertAndDate(String concertId, LocalDateTime concertDateTime);
    long countSeatsByStatus(String concertId, LocalDateTime concertDateTime, SeatStatus status);
    List<Seat> findExpiredReservations();
}
