package kr.hhplus.be.server.infrastructure.persistence;

import jakarta.persistence.QueryHint;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.infrastructure.persistence.entity.SeatReservationEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatReservationJpaRepository extends JpaRepository<SeatReservationEntity, Long> {

    Optional<SeatReservationEntity> findByConcertIdAndSeatNumber(Long concertId, Integer seatNumber);

    // 비관적 락으로 좌석 조회 (동시성 제어)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SeatReservationEntity s WHERE s.concertId = :concertId AND s.seatNumber = :seatNumber")
    Optional<SeatReservationEntity> findByConcertIdAndSeatNumberWithLock(@Param("concertId") Long concertId,
                                                                         @Param("seatNumber") Integer seatNumber);

    // 콘서트별 상태별 좌석 조회
    List<SeatReservationEntity> findByConcertIdAndStatus(Long concertId, SeatStatus status);

    // 예약 가능한 좌석 조회 (AVAILABLE + 만료된 RESERVED)
    @Query("SELECT s FROM SeatReservationEntity s WHERE s.concertId = :concertId AND " +
            "(s.status = 'AVAILABLE' OR (s.status = 'RESERVED' AND s.expiresAt < :now))")
    List<SeatReservationEntity> findAvailableSeats(@Param("concertId") Long concertId,
                                                   @Param("now") LocalDateTime now);

    // 만료된 예약 조회
    @Query("SELECT s FROM SeatReservationEntity s WHERE s.status = 'RESERVED' AND s.expiresAt < :now")
    List<SeatReservationEntity> findExpiredReservations(@Param("now") LocalDateTime now);

    // 만료된 예약 일괄 해제
    @Modifying
    @Query("UPDATE SeatReservationEntity s SET s.status = 'AVAILABLE', s.userId = null, " +
            "s.reservedAt = null, s.expiresAt = null, s.updatedAt = :now " +
            "WHERE s.status = 'RESERVED' AND s.expiresAt < :now")
    void releaseExpiredReservations(@Param("now") LocalDateTime now);

    // 사용자별 예약 조회
    List<SeatReservationEntity> findByUserIdAndStatus(Long userId, SeatStatus status);

    // 콘서트별 통계 (성능 최적화를 위한 커버링 인덱스 활용)
    @Query("SELECT s.status, COUNT(s) FROM SeatReservationEntity s WHERE s.concertId = :concertId GROUP BY s.status")
    List<Object[]> countSeatsByStatus(@Param("concertId") Long concertId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatReservationEntity s where s.concertId = :concertId and s.seatNumber = :seatNumber")
    Optional<SeatReservationEntity> findAndLockByConcertIdAndSeatNumber(@Param("concertId") Long concertId, @Param("seatNumber") Integer seatNumber);

    // 조건부 UPDATE - 좌석 예약 (원자적 연산)
    @Modifying
    @Query("UPDATE SeatReservationEntity s " +
            "SET s.status = :newStatus, s.userId = :userId, s.reservedAt = :reservedAt, s.expiresAt = :expiresAt, s.updatedAt = :now " +
            "WHERE s.concertId = :concertId AND s.seatNumber = :seatNumber AND s.status = 'AVAILABLE'")
    int reserveSeatConditionally(@Param("concertId") Long concertId,
                                 @Param("seatNumber") Integer seatNumber,
                                 @Param("userId") Long userId,
                                 @Param("newStatus") SeatStatus newStatus,
                                 @Param("reservedAt") LocalDateTime reservedAt,
                                 @Param("expiresAt") LocalDateTime expiresAt,
                                 @Param("now") LocalDateTime now);

    // 조건부 UPDATE - 좌석 확정 (원자적 연산)
    @Modifying
    @Query("UPDATE SeatReservationEntity s " +
            "SET s.status = 'SOLD', s.expiresAt = null, s.updatedAt = :now " +
            "WHERE s.concertId = :concertId AND s.seatNumber = :seatNumber " +
            "AND s.status = 'RESERVED' AND s.userId = :userId AND s.expiresAt > :now")
    int confirmSeatConditionally(@Param("concertId") Long concertId,
                                 @Param("seatNumber") Integer seatNumber,
                                 @Param("userId") Long userId,
                                 @Param("now") LocalDateTime now);

    // 배치 UPDATE - 만료된 예약 해제 (스케줄러용)
    @Modifying
    @Query("UPDATE SeatReservationEntity s " +
            "SET s.status = 'AVAILABLE', s.userId = null, s.reservedAt = null, s.expiresAt = null, s.updatedAt = :now " +
            "WHERE s.status = 'RESERVED' AND s.expiresAt < :now")
    int releaseExpiredReservationsBatch(@Param("now") LocalDateTime now);

}
