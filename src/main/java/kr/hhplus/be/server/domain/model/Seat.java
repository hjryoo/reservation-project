package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;

public class Seat {
    private final String concertId;
    private final LocalDateTime concertDateTime;
    private final int seatNumber;
    private final String position;
    private final SeatStatus status;
    private final LocalDateTime reservedUntil;

    private Seat(String concertId, LocalDateTime concertDateTime, int seatNumber,
                 String position, SeatStatus status, LocalDateTime reservedUntil) {
        this.concertId = concertId;
        this.concertDateTime = concertDateTime;
        this.seatNumber = seatNumber;
        this.position = position;
        this.status = status;
        this.reservedUntil = reservedUntil;
    }

    public static Seat create(String concertId, LocalDateTime concertDateTime,
                              int seatNumber, String position) {
        return new Seat(concertId, concertDateTime, seatNumber, position,
                SeatStatus.AVAILABLE, null);
    }

    // 좌석 예약 가능 여부 확인
    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    // 임시 예약된 좌석이 만료되었는지 확인
    public boolean isReservationExpired() {
        return status == SeatStatus.RESERVED &&
                reservedUntil != null &&
                LocalDateTime.now().isAfter(reservedUntil);
    }

    // Getters
    public String getConcertId() { return concertId; }
    public LocalDateTime getConcertDateTime() { return concertDateTime; }
    public int getSeatNumber() { return seatNumber; }
    public String getPosition() { return position; }
    public SeatStatus getStatus() { return status; }
    public LocalDateTime getReservedUntil() { return reservedUntil; }
}