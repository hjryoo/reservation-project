package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.domain.model.ConcertStatus;
import kr.hhplus.be.server.infrastructure.persistence.entity.ConcertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}