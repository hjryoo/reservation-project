package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.ConcertStatus;
import kr.hhplus.be.server.infrastructure.persistence.entity.ConcertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

// 타입 불일치 해결: JpaRepository<ConcertEntity, Long>
public interface SpringDataConcertRepository extends JpaRepository<ConcertEntity, Long> {

    List<ConcertEntity> findByStatus(ConcertStatus status);

    List<ConcertEntity> findByArtist(String artist);

    List<ConcertEntity> findByVenue(String venue);

    @Query("SELECT c FROM ConcertEntity c WHERE c.status = :status AND c.createdAt >= :currentDate ORDER BY c.createdAt ASC")
    List<ConcertEntity> findByStatusAndCreatedAtAfter(
            @Param("status") ConcertStatus status,
            @Param("currentDate") LocalDateTime currentDate
    );

    @Query("SELECT c FROM ConcertEntity c WHERE c.artist = :artist AND c.createdAt >= :currentDate ORDER BY c.createdAt ASC")
    List<ConcertEntity> findByArtistAndCreatedAtAfter(
            @Param("artist") String artist,
            @Param("currentDate") LocalDateTime currentDate
    );

    @Modifying
    @Query(value = """
        UPDATE concerts 
        SET available_seats = available_seats - 1,
            status = CASE WHEN available_seats - 1 = 0 THEN 'SOLD_OUT' ELSE status END,
            sold_out_at = CASE WHEN available_seats - 1 = 0 THEN NOW() ELSE sold_out_at END,
            updated_at = NOW()
        WHERE id = :concertId AND available_seats > 0
        """, nativeQuery = true)
    int decreaseAvailableSeatsAtomically(@Param("concertId") Long concertId);

}