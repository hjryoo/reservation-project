package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;

public class ConcertDate {
    // 기술적 필드 (mutable)
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 비즈니스 필드 (final)
    private final Long concertId;
    private final LocalDateTime concertDateTime;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Integer totalSeats;
    private Integer availableSeats;  // mutable - 예약에 따라 변경
    private ConcertDateStatus status; // mutable - 상태 변경 가능

    public enum ConcertDateStatus {
        SCHEDULED,   // 예정
        AVAILABLE,   // 예약 가능
        SOLD_OUT,    // 매진
        CANCELLED,   // 취소
        COMPLETED    // 공연 완료
    }

    private ConcertDate(Long concertId, LocalDateTime concertDateTime,
                        LocalDateTime startTime, LocalDateTime endTime,
                        Integer totalSeats) {
        this.concertId = concertId;
        this.concertDateTime = concertDateTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
        this.status = ConcertDateStatus.SCHEDULED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static ConcertDate create(Long concertId, LocalDateTime concertDateTime,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     Integer totalSeats) {
        validateInput(concertId, concertDateTime, startTime, endTime, totalSeats);
        return new ConcertDate(concertId, concertDateTime, startTime, endTime, totalSeats);
    }

    // 비즈니스 규칙
    public boolean isBookingAvailable() {
        return status == ConcertDateStatus.AVAILABLE &&
                availableSeats > 0 &&
                concertDateTime.isAfter(LocalDateTime.now());
    }

    public boolean hasAvailableSeats() {
        return availableSeats > 0;
    }

    public void openBooking() {
        if (status != ConcertDateStatus.SCHEDULED) {
            throw new IllegalStateException("예정 상태의 공연만 예약을 열 수 있습니다.");
        }
        this.status = ConcertDateStatus.AVAILABLE;
        this.updatedAt = LocalDateTime.now();
    }

    public void decreaseAvailableSeats() {
        if (availableSeats <= 0) {
            throw new IllegalStateException("더 이상 예약 가능한 좌석이 없습니다.");
        }
        this.availableSeats--;

        // 매진 체크
        if (availableSeats == 0) {
            this.status = ConcertDateStatus.SOLD_OUT;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseAvailableSeats() {
        if (availableSeats >= totalSeats) {
            throw new IllegalStateException("가용 좌석이 총 좌석을 초과할 수 없습니다.");
        }
        this.availableSeats++;

        // 매진에서 예약 가능으로 변경
        if (status == ConcertDateStatus.SOLD_OUT) {
            this.status = ConcertDateStatus.AVAILABLE;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == ConcertDateStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 공연은 취소할 수 없습니다.");
        }
        this.status = ConcertDateStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        if (concertDateTime.isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("아직 공연 시간이 되지 않았습니다.");
        }
        this.status = ConcertDateStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateInput(Long concertId, LocalDateTime concertDateTime,
                                      LocalDateTime startTime, LocalDateTime endTime,
                                      Integer totalSeats) {
        if (concertId == null) {
            throw new IllegalArgumentException("콘서트 ID는 필수입니다.");
        }
        if (concertDateTime == null) {
            throw new IllegalArgumentException("공연 날짜는 필수입니다.");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("시작/종료 시간은 필수입니다.");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 이전이어야 합니다.");
        }
        if (totalSeats == null || totalSeats <= 0) {
            throw new IllegalArgumentException("총 좌석 수는 1 이상이어야 합니다.");
        }
        if (concertDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("공연 날짜는 현재 시간 이후여야 합니다.");
        }
    }

    // Infrastructure 전용 메서드
    public void assignTechnicalFields(Long id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isBookable() {
        return status == ConcertDateStatus.AVAILABLE &&
                availableSeats > 0 &&
                concertDateTime.isAfter(LocalDateTime.now());
    }

    public void assignId(Long id) {
        this.id = id;
    }

    // Getters
    public Long getId() { return id; }
    public Long getConcertId() { return concertId; }
    public LocalDateTime getConcertDateTime() { return concertDateTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Integer getTotalSeats() { return totalSeats; }
    public Integer getAvailableSeats() { return availableSeats; }
    public ConcertDateStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
