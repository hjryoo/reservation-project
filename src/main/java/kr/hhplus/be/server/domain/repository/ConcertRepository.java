package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConcertRepository {
    List<ConcertDate> findAvailableDatesByMonth(LocalDate month, int limit);
    List<ConcertDate> findAllAvailableDates(int limit);
    Optional<Concert> findByConcertId(String concertId);
    Optional<ConcertDate> findConcertDate(String concertId, LocalDateTime concertDateTime);
}

