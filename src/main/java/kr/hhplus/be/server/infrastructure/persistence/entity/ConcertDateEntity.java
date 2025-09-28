package kr.hhplus.be.server.infrastructure.persistence.entity;

import jakarta.persistence.*;
import kr.hhplus.be.server.domain.model.ConcertDate.ConcertDateStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "concert_dates",
        indexes = {
                @Index(name = "idx_concert_date_concert_id", columnList = "concert_id"),
                @Index(name = "idx_concert_date_datetime", columnList = "concert_date_time"),
                @Index(name = "idx_concert_date_status", columnList = "status"),
                @Index(name = "idx_concert_date_available", columnList = "concert_id, status, available_seats"),
                @Index(name = "idx_concert_date_time_range", columnList = "concert_date_time, start_time, end_time")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_concert_datetime", columnNames = {"concert_id", "concert_date_time"})
        }
)
public class ConcertDateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "concert_date_time", nullable = false)
    private LocalDateTime concertDateTime;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "available_seats", nullable = false)
    private Integer availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConcertDateStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // 연관관계 매핑 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_concert_date_concert"))
    private ConcertEntity concert;

    // JPA용 기본 생성자
    protected ConcertDateEntity() {}

    public ConcertDateEntity(LocalDateTime concertDateTime, LocalDateTime startTime,
                             LocalDateTime endTime, Integer totalSeats,
                             Integer availableSeats, ConcertDateStatus status) {
        this.concertDateTime = concertDateTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        this.status = status;
    }

    // 비즈니스 규칙 검증
    public boolean isAvailable() {
        return status == ConcertDateStatus.AVAILABLE &&
                availableSeats > 0 &&
                concertDateTime.isAfter(LocalDateTime.now());
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getConcertDateTime() { return concertDateTime; }
    public void setConcertDateTime(LocalDateTime concertDateTime) { this.concertDateTime = concertDateTime; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public ConcertDateStatus getStatus() { return status; }
    public void setStatus(ConcertDateStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public ConcertEntity getConcert() { return concert; }
    public void setConcert(ConcertEntity concert) { this.concert = concert; }

    public Long getConcertId() {
        return concert != null ? concert.getId() : null;
    }

    public void setConcertById(Long concertId) {
        if (concertId != null) {
            ConcertEntity concertEntity = new ConcertEntity();
            concertEntity.setId(concertId);
            this.concert = concertEntity;
        }
    }

}
