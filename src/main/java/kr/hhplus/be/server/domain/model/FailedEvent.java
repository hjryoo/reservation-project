package kr.hhplus.be.server.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType; // 예: "ReservationCompleted"

    @Column(nullable = false)
    private Long reservationId; // 검색 편의성을 위한 키 컬럼

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload; // 이벤트 전체 데이터 (JSON)

    @Column(columnDefinition = "TEXT")
    private String failureReason; // 에러 메시지

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FailedEventStatus status = FailedEventStatus.PENDING_RETRY;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastRetryAt;

    public enum FailedEventStatus {
        PENDING_RETRY, RETRYING, RESOLVED, FAILED
    }

    public static FailedEvent create(String eventType, Long reservationId, String payload, String failureReason) {
        FailedEvent event = new FailedEvent();
        event.eventType = eventType;
        event.reservationId = reservationId;
        event.payload = payload;
        event.failureReason = failureReason;
        return event;
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
    }

    public void markAsResolved() {
        this.status = FailedEventStatus.RESOLVED;
    }

    public void markAsFailed() {
        this.status = FailedEventStatus.FAILED;
    }
}
