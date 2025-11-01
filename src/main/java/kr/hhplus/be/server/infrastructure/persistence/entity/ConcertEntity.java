package kr.hhplus.be.server.infrastructure.persistence.entity;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.model.ConcertStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.model.ConcertStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "concerts",
        indexes = {
                @Index(name = "idx_concert_artist", columnList = "artist"),
                @Index(name = "idx_concert_status", columnList = "status"),
                @Index(name = "idx_concert_created", columnList = "created_at"),
                @Index(name = "idx_concert_artist_status", columnList = "artist, status"),
                @Index(name = "idx_concert_soldout", columnList = "sold_out_at")
        }
)
public class ConcertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String artist;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConcertStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 신규: 매진 추적 필드
    @Column(name = "booking_open_at")
    private LocalDateTime bookingOpenAt;

    @Column(name = "sold_out_at")
    private LocalDateTime soldOutAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ConcertDateEntity> concertDates = new ArrayList<>();

    protected ConcertEntity() {}

    public ConcertEntity(String title, String artist, String venue,
                         Integer totalSeats, Integer availableSeats, Long price,
                         ConcertStatus status) {
        this.title = title;
        this.artist = artist;
        this.venue = venue;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.price = price;
        this.status = status;
    }

    public void addConcertDate(ConcertDateEntity concertDate) {
        concertDates.add(concertDate);
        concertDate.setConcert(this);
    }

    public void removeConcertDate(ConcertDateEntity concertDate) {
        concertDates.remove(concertDate);
        concertDate.setConcert(null);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public Long getPrice() { return price; }
    public void setPrice(Long price) { this.price = price; }

    public ConcertStatus getStatus() { return status; }
    public void setStatus(ConcertStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public LocalDateTime getBookingOpenAt() { return bookingOpenAt; }
    public void setBookingOpenAt(LocalDateTime bookingOpenAt) { this.bookingOpenAt = bookingOpenAt; }

    public LocalDateTime getSoldOutAt() { return soldOutAt; }
    public void setSoldOutAt(LocalDateTime soldOutAt) { this.soldOutAt = soldOutAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<ConcertDateEntity> getConcertDates() { return concertDates; }
    public void setConcertDates(List<ConcertDateEntity> concertDates) { this.concertDates = concertDates; }
}