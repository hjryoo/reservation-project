package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class QueueToken {
    private Long id;
    private Integer position;

    private final String tokenValue;
    private final Long userId;
    private final Long concertId;
    private final QueueStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final LocalDateTime enteredAt;

    private QueueToken(String tokenValue, Long userId, Long concertId,
                       QueueStatus status, LocalDateTime createdAt,
                       LocalDateTime expiresAt, LocalDateTime enteredAt) {
        this.tokenValue = tokenValue;
        this.userId = userId;
        this.concertId = concertId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.enteredAt = enteredAt;
    }


    public static QueueToken createWaitingToken(Long userId, Long concertId) {
        return new QueueToken(
                UUID.randomUUID().toString(),
                userId, concertId,
                QueueStatus.WAITING,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                null
        );
    }

    public static QueueToken createWithStatus(String tokenValue, Long userId, Long concertId,
                                              QueueStatus status, LocalDateTime createdAt,
                                              LocalDateTime expiresAt, LocalDateTime enteredAt) {
        return new QueueToken(tokenValue, userId, concertId, status, createdAt, expiresAt, enteredAt);
    }

    public void assignId(Long id) {
        this.id = id;
    }

    public void assignPosition(Integer position) {
        this.position = position;
    }

    public void assignTechnicalFields(Long id, Integer position) {
        this.id = id;
        this.position = position;
    }


    // 대기열 진입 (ACTIVE 상태로 변경)
    public QueueToken activate() {
        if (status != QueueStatus.WAITING) {
            throw new IllegalStateException("대기 중인 토큰만 활성화할 수 있습니다.");
        }

        QueueToken activated = new QueueToken(
                this.tokenValue,
                this.userId,
                this.concertId,
                QueueStatus.ACTIVE,
                this.createdAt,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now()
        );

        // 기술적 필드 복사
        activated.id = this.id;
        activated.position = this.position;

        return activated;
    }


    // 토큰 만료
    public QueueToken expire() {
        QueueToken expired = new QueueToken(
                this.tokenValue,
                this.userId,
                this.concertId,
                QueueStatus.EXPIRED,
                this.createdAt,
                this.expiresAt,
                this.enteredAt
        );

        // 기술적 필드 복사
        expired.id = this.id;
        expired.position = this.position;

        return expired;
    }

    // 토큰 완료 (예약 완료 후)
    public QueueToken complete() {
        if (status != QueueStatus.ACTIVE) {
            throw new IllegalStateException("활성 토큰만 완료할 수 있습니다.");
        }

        QueueToken completed = new QueueToken(
                this.tokenValue,
                this.userId,
                this.concertId,
                QueueStatus.COMPLETED,
                this.createdAt,
                this.expiresAt,
                this.enteredAt
        );

        completed.id = this.id;
        completed.position = this.position;

        return completed;
    }

    // 비즈니스 로직 - 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    // 비즈니스 로직 - 활성 상태 여부
    public boolean isActive() {
        return status == QueueStatus.ACTIVE && !isExpired();
    }

    // Getters
    public Long getId() { return id; }
    public String getTokenValue() { return tokenValue; }
    public Long getUserId() { return userId; }
    public Long getConcertId() { return concertId; }
    public QueueStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Integer getPosition() { return position; }
    public LocalDateTime getEnteredAt() { return enteredAt; }
}

