package kr.hhplus.be.server.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SeatReservation {
    private Long id;
    private final Long concertId;
    private final Integer seatNumber;
    private Long userId;
    private SeatStatus status;
    private LocalDateTime reservedAt;
    private LocalDateTime expiresAt;
    private final Long price;

    // 추가된 필드
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private SeatReservation(Long concertId, Integer seatNumber, Long userId,
                            SeatStatus status, LocalDateTime reservedAt,
                            LocalDateTime expiresAt, Long price) {
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.userId = userId;
        this.status = status;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
        this.price = price;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static SeatReservation createAvailableSeat(Long concertId, Integer seatNumber, Long price) {
        return new SeatReservation(
                concertId,
                seatNumber,
                null,
                SeatStatus.AVAILABLE,
                null,
                null,
                price
        );
    }

    public static SeatReservation createTemporaryReservation(Long concertId, Integer seatNumber,
                                                             Long userId, Long price) {
        LocalDateTime now = LocalDateTime.now();
        return new SeatReservation(
                concertId,
                seatNumber,
                userId,
                SeatStatus.RESERVED,
                now,
                now.plusMinutes(5),
                price
        );
    }

    public static SeatReservation createConfirmedReservation(Long concertId, Integer seatNumber,
                                                             Long userId, Long price) {
        LocalDateTime now = LocalDateTime.now();
        return new SeatReservation(
                concertId,
                seatNumber,
                userId,
                SeatStatus.SOLD,
                now,
                null,
                price
        );
    }

    public static SeatReservation createWithTimes(Long concertId, Integer seatNumber,
                                                  Long userId, Long price,
                                                  LocalDateTime reservedAt, LocalDateTime expiresAt) {
        return new SeatReservation(
                concertId,
                seatNumber,
                userId,
                SeatStatus.RESERVED,
                reservedAt,
                expiresAt,
                price
        );
    }

    /**
     * Jackson 역직렬화를 위한 생성자
     */
    @JsonCreator
    private SeatReservation(
            @JsonProperty("id") Long id,
            @JsonProperty("concertId") Long concertId,
            @JsonProperty("seatNumber") Integer seatNumber,
            @JsonProperty("userId") Long userId,
            @JsonProperty("price") Long price,
            @JsonProperty("status") SeatStatus status,
            @JsonProperty("reservedAt") LocalDateTime reservedAt,
            @JsonProperty("expiresAt") LocalDateTime expiresAt,
            @JsonProperty("createdAt") LocalDateTime createdAt,
            @JsonProperty("updatedAt") LocalDateTime updatedAt
    ) {
        this.id = id;
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.userId = userId;
        this.price = price;
        this.status = status;
        this.reservedAt = reservedAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public boolean isExpired() {
        if (status != SeatStatus.RESERVED || expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE || isExpired();
    }

    public boolean isReservedBy(Long userId) {
        return status == SeatStatus.RESERVED &&
                this.userId != null &&
                this.userId.equals(userId) &&
                !isExpired();
    }

    public boolean canBeConfirmed(Long userId) {
        return this.status == SeatStatus.RESERVED &&
                this.userId.equals(userId) &&
                (this.expiresAt != null && this.expiresAt.isAfter(LocalDateTime.now()));
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public void assignTimestamps(LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void reserveTemporarily(Long userId) {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("예약 가능한 상태의 좌석이 아닙니다.");
        }
        this.status = SeatStatus.RESERVED;
        this.userId = userId;
        this.reservedAt = LocalDateTime.now();
        this.expiresAt = this.reservedAt.plusMinutes(5);
        this.updatedAt = LocalDateTime.now();
    }

    public void reserve(Long userId) {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("이미 예약되었거나 판매된 좌석입니다.");
        }
        this.status = SeatStatus.RESERVED;
        this.userId = userId;
        this.expiresAt = LocalDateTime.now().plusMinutes(5);
        this.updatedAt = LocalDateTime.now();
    }

    public void confirm() {
        if (this.status != SeatStatus.RESERVED) {
            throw new IllegalStateException("예약 상태의 좌석만 확정할 수 있습니다.");
        }
        this.status = SeatStatus.SOLD;
        this.expiresAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.userId = null;
        this.expiresAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void forceExpire(LocalDateTime time) {
        this.expiresAt = time;
    }

    // Getters
    public Long getId() { return id; }
    public Long getConcertId() { return concertId; }
    public Integer getSeatNumber() { return seatNumber; }
    public Long getUserId() { return userId; }
    public SeatStatus getStatus() { return status; }
    public LocalDateTime getReservedAt() { return reservedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Long getPrice() { return price; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

