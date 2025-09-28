package kr.hhplus.be.server.domain.port.in;

import kr.hhplus.be.server.domain.model.Reservation;

public interface ReserveSeatUseCase {
    Reservation reserve(ReserveSeatCommand command);

    record ReserveSeatCommand(Long userId, Long concertId, int seatNumber) {
    }
}
