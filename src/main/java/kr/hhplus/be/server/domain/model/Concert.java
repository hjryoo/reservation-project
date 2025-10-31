package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class Concert {
    // 기술적 필드 (mutable)
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 비즈니스 필드 (final)
    private final String title;
    private final String artist;
    private final String venue;
    private final Integer totalSeats;
    private Integer availableSeats;
    private final Long price;
    private ConcertStatus status;

    // 매진 추적 필드 (신규)
    private LocalDateTime bookingOpenAt;  // 예약 시작 시간
    private LocalDateTime soldOutAt;      // 매진 완료 시간

    private Concert(String title, String artist, String venue,
                    Integer totalSeats, Long price) {
        this.title = title;
        this.artist = artist;
        this.venue = venue;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
        this.price = price;
        this.status = ConcertStatus.AVAILABLE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static Concert create(String title, String artist, String venue,
                                 Integer totalSeats, Long price) {
        validateInput(title, artist, venue, totalSeats, price);
        return new Concert(title, artist, venue, totalSeats, price);
    }

    // 기존 비즈니스 규칙
    public boolean isBookingAvailable() {
        return status == ConcertStatus.AVAILABLE && availableSeats > 0;
    }

    public void updateStatus(ConcertStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("상태는 null일 수 없습니다.");
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void decreaseAvailableSeats() {
        if (availableSeats <= 0) {
            throw new IllegalStateException("더 이상 예약 가능한 좌석이 없습니다.");
        }
        this.availableSeats--;
        this.updatedAt = LocalDateTime.now();

        // 매진 체크
        if (this.availableSeats == 0 && this.status != ConcertStatus.SOLD_OUT) {
            markAsSoldOut();
        }
    }

    public void increaseAvailableSeats() {
        if (availableSeats >= totalSeats) {
            throw new IllegalStateException("가용 좌석이 총 좌석을 초과할 수 없습니다.");
        }
        this.availableSeats++;
        this.updatedAt = LocalDateTime.now();
    }

    // 신규: 예약 시작 시간 설정
    public void openBooking() {
        if (this.bookingOpenAt != null) {
            throw new IllegalStateException("이미 예약이 시작된 콘서트입니다.");
        }
        this.bookingOpenAt = LocalDateTime.now();
        this.status = ConcertStatus.AVAILABLE;
        this.updatedAt = LocalDateTime.now();
    }

    // 신규: 매진 처리
    public void markAsSoldOut() {
        if (this.status == ConcertStatus.SOLD_OUT) {
            throw new IllegalStateException("이미 매진된 콘서트입니다.");
        }

        this.status = ConcertStatus.SOLD_OUT;
        this.soldOutAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 신규: 매진 소요 시간 계산 (초 단위)
    public Long calculateSoldOutDurationSeconds() {
        if (this.soldOutAt == null || this.bookingOpenAt == null) {
            throw new IllegalStateException("매진 시간 또는 예약 시작 시간이 기록되지 않았습니다.");
        }
        return ChronoUnit.SECONDS.between(this.bookingOpenAt, this.soldOutAt);
    }

    public Long calculateSoldOutDurationMillis() {
        if (this.soldOutAt == null || this.bookingOpenAt == null) {
            throw new IllegalStateException("매진 시간 또는 예약 시작 시간이 기록되지 않았습니다.");
        }
        return ChronoUnit.MILLIS.between(this.bookingOpenAt, this.soldOutAt);
    }

    // 신규: 매진 여부 확인
    public boolean isSoldOut() {
        return this.status == ConcertStatus.SOLD_OUT;
    }

    private static void validateInput(String title, String artist, String venue,
                                      Integer totalSeats, Long price) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("콘서트 제목은 필수입니다.");
        }
        if (artist == null || artist.trim().isEmpty()) {
            throw new IllegalArgumentException("아티스트명은 필수입니다.");
        }
        if (venue == null || venue.trim().isEmpty()) {
            throw new IllegalArgumentException("공연장은 필수입니다.");
        }
        if (totalSeats == null || totalSeats <= 0) {
            throw new IllegalArgumentException("총 좌석 수는 1 이상이어야 합니다.");
        }
        if (price == null || price < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
    }

    // Infrastructure 전용 메서드
    public void assignTechnicalFields(Long id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    // 신규: 매진 관련 기술 필드 할당
    public void assignSoldOutFields(LocalDateTime bookingOpenAt, LocalDateTime soldOutAt) {
        this.bookingOpenAt = bookingOpenAt;
        this.soldOutAt = soldOutAt;
    }

    // Getters
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getVenue() { return venue; }
    public Integer getTotalSeats() { return totalSeats; }
    public Integer getAvailableSeats() { return availableSeats; }
    public Long getPrice() { return price; }
    public ConcertStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getBookingOpenAt() { return bookingOpenAt; }
    public LocalDateTime getSoldOutAt() { return soldOutAt; }
}
