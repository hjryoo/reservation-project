package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.ConcertDate;
import kr.hhplus.be.server.domain.model.ConcertDate.ConcertDateStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConcertDateRepository {
    ConcertDate save(ConcertDate concertDate);
    List<ConcertDate> saveAll(List<ConcertDate> concertDates);
    Optional<ConcertDate> findById(Long id);
    List<ConcertDate> findAll();
    List<ConcertDate> findAllById(List<Long> ids);

    // 삭제 메서드들
    void delete(ConcertDate concertDate);
    void deleteById(Long id);
    void deleteAll();
    void deleteAll(List<ConcertDate> concertDates);
    void deleteAllById(List<Long> ids);

    // 배치 삭제 메서드들 (JpaRepository와 유사)
    void deleteAllInBatch();
    void deleteAllInBatch(List<ConcertDate> concertDates);
    void deleteAllByIdInBatch(List<Long> ids);

    // 존재 확인 및 개수
    boolean existsById(Long id);
    long count();

    // 도메인 특화 메서드들
    List<ConcertDate> findByConcertId(Long concertId);
    Optional<ConcertDate> findByConcertIdAndDateTime(Long concertId, LocalDateTime dateTime);
    List<ConcertDate> findAvailableDatesByMonth(LocalDate month, int limit);
    List<ConcertDate> findAllAvailableDates(int limit);
    List<ConcertDate> findByStatus(ConcertDateStatus status);
    List<ConcertDate> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);
    List<ConcertDate> findUpcomingDates(LocalDateTime fromDate, int limit);

    // 배치 업데이트 메서드들
    int bulkUpdateStatus(ConcertDateStatus newStatus, ConcertDateStatus oldStatus);
    int bulkUpdateAvailableSeats(Long concertId, int newAvailableSeats);
}
