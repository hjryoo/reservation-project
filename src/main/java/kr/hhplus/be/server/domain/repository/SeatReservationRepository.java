package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatReservationRepository {
    SeatReservation save(SeatReservation reservation);

    Optional<SeatReservation> findById(Long id);

    Optional<SeatReservation> findByConcertIdAndSeatNumber(Long concertId, Integer seatNumber);

    // 동시성 제어를 위한 비관적 락
    Optional<SeatReservation> findByConcertIdAndSeatNumberWithLock(Long concertId, Integer seatNumber);

    // 좌석 상태별 조회
    List<SeatReservation> findByConcertIdAndStatus(Long concertId, SeatStatus status);

    List<SeatReservation> findAvailableSeats(Long concertId);

    // 만료된 예약 처리
    List<SeatReservation> findExpiredReservations(LocalDateTime now);

    void releaseExpiredReservations(LocalDateTime now);

    // 특정 사용자의 예약 조회
    List<SeatReservation> findByUserIdAndStatus(Long userId, SeatStatus status);

    Optional<SeatReservation> findByConcertIdAndSeatNumberForUpdate(Long concertId, Integer seatNumber);

    List<SeatReservation> findAll();

    /**
     * 좌석을 조건부로 예약 (원자적 연산)
     * AVAILABLE 상태인 좌석만 RESERVED로 변경
     * @return 업데이트된 행 수 (성공 시 1, 실패 시 0)
     */
    int reserveSeatConditionally(Long concertId, Integer seatNumber, Long userId, LocalDateTime expiresAt);

    /**
     * 좌석을 조건부로 확정 (원자적 연산)
     * RESERVED 상태이면서 만료되지 않고 해당 사용자의 예약만 SOLD로 변경
     *
     * @return 업데이트된 행 수 (성공 시 1, 실패 시 0)
     */
    int confirmSeatConditionally(Long concertId, Integer seatNumber, Long userId);

    /**
     * 만료된 예약을 일괄 해제 (배치 처리용)
     *
     * @return 해제된 예약 수
     */
    int releaseExpiredReservationsBatch(LocalDateTime now);
}