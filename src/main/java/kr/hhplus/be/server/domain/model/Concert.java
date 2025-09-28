package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;

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
    private Integer availableSeats;  // mutable - 예약에 따라 변경
    private final Long price;
    private ConcertStatus status;    // mutable - 상태 변경 가능

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

    // 비즈니스 규칙
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
    }

    public void increaseAvailableSeats() {
        if (availableSeats >= totalSeats) {
            throw new IllegalStateException("가용 좌석이 총 좌석을 초과할 수 없습니다.");
        }
        this.availableSeats++;
        this.updatedAt = LocalDateTime.now();
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
}
