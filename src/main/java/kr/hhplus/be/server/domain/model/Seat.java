package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;

public class Seat {
    // 기술적 필드 (mutable)
    private Long id;

    // 비즈니스 필드 (final)
    private final Long concertId;
    private final Integer seatNumber;
    private final SeatStatus status;
    private final Long reservedByUserId;
    private final String position;
    private final LocalDateTime reservedUntil;

    private Seat(Long concertId, Integer seatNumber, SeatStatus status,
                 Long reservedByUserId, String position, LocalDateTime reservedUntil) {
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.status = status;
        this.reservedByUserId = reservedByUserId;
        this.position = position;
        this.reservedUntil = reservedUntil;
    }

    public static Seat available(Long concertId, Integer seatNumber) {
        return new Seat(concertId, seatNumber, SeatStatus.AVAILABLE, null, null, null);
    }

    public static Seat available(Long concertId, Integer seatNumber, String position) {
        return new Seat(concertId, seatNumber, SeatStatus.AVAILABLE, null, position, null);
    }

    // 비즈니스 규칙
    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public Seat reserve(Long userId) {
        if (!isAvailable()) {
            throw new IllegalStateException("이미 예약된 좌석입니다.");
        }
        return new Seat(this.concertId, this.seatNumber, SeatStatus.RESERVED,
                userId, this.position, reservedUntil);
    }

    public Seat markAsSold() {
        if (status != SeatStatus.RESERVED) {
            throw new IllegalStateException("예약된 좌석만 판매 완료 처리할 수 있습니다.");
        }
        return new Seat(this.concertId, this.seatNumber, SeatStatus.SOLD,
                this.reservedByUserId, this.position, this.reservedUntil);
    }

    // Infrastructure 전용 메서드
    public void assignId(Long id) {
        this.id = id;
    }

    // Getters
    public Long getId() { return id; }
    public Long getConcertId() { return concertId; }
    public Integer getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status; }
    public Long getReservedByUserId() { return reservedByUserId; }
    public String getPosition() { return position; }
    public LocalDateTime getReservedUntil() { return reservedUntil; }

    // SeatStatus enum
    public enum SeatStatus {
        AVAILABLE,  // 예약 가능
        RESERVED,   // 임시 예약 (5분간)
        SOLD        // 결제 완료 (확정)
    }

}
