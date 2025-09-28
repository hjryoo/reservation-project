package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.ConcertDate.ConcertDateStatus;
import kr.hhplus.be.server.infrastructure.persistence.entity.ConcertDateEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpringDataConcertDateRepository extends JpaRepository<ConcertDateEntity, Long> {

    List<ConcertDateEntity> findByConcert_Id(Long concertId);

    Optional<ConcertDateEntity> findByConcert_IdAndConcertDateTime(Long concertId, LocalDateTime concertDateTime);

    List<ConcertDateEntity> findByStatus(ConcertDateStatus status);

    List<ConcertDateEntity> findByConcertDateTimeBetween(LocalDateTime startDate, LocalDateTime endDate);

    // LIMIT 문제 해결: Pageable 사용
    @Query("SELECT cd FROM ConcertDateEntity cd WHERE " +
            "cd.concertDateTime >= :startDate AND cd.concertDateTime <= :endDate AND " +
            "cd.availableSeats > 0 AND cd.status = 'AVAILABLE' " +
            "ORDER BY cd.concertDateTime ASC")
    List<ConcertDateEntity> findAvailableDatesByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT cd FROM ConcertDateEntity cd WHERE " +
            "cd.concertDateTime > :now AND cd.availableSeats > 0 AND cd.status = 'AVAILABLE' " +
            "ORDER BY cd.concertDateTime ASC")
    List<ConcertDateEntity> findAvailableDates(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("SELECT cd FROM ConcertDateEntity cd WHERE " +
            "cd.concertDateTime >= :fromDate " +
            "ORDER BY cd.concertDateTime ASC")
    List<ConcertDateEntity> findUpcomingDates(
            @Param("fromDate") LocalDateTime fromDate,
            Pageable pageable
    );
}