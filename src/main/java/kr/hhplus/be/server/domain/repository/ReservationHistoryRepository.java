package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.ReservationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationHistoryRepository extends JpaRepository<ReservationHistory, Long> {
    boolean existsByReservationId(Long reservationId);
}

