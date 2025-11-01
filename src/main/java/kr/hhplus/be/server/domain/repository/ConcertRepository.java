package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.ConcertStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConcertRepository {
    Concert save(Concert concert);

    Optional<Concert> findById(Long id);

    List<Concert> findAll();

    List<Concert> findAvailableConcerts(ConcertStatus status, LocalDateTime currentDate);

    List<Concert> findConcertsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    List<Concert> findByArtistAndFutureDates(String artist, LocalDateTime currentDate);

    List<Concert> findByArtist(String artist);

    List<Concert> findByVenue(String venue);

    List<Concert> findByStatus(ConcertStatus status);

    // 신규: 매진된 콘서트 조회
    List<Concert> findSoldOutConcerts();

    @Transactional
    void delete(Concert concert);

    @Transactional
    void deleteById(Long id);

    boolean existsById(Long id);

    long count();
}
