package kr.hhplus.be.server.infrastructure.persistence.entity;

import kr.hhplus.be.server.domain.model.TokenStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reservation_tokens",
        indexes = {
                @Index(name = "idx_reservation_token_token", columnList = "token"),
                @Index(name = "idx_reservation_token_user_status", columnList = "user_id, status"),
                @Index(name = "idx_reservation_token_status_expires", columnList = "status, expires_at"),
                @Index(name = "idx_reservation_token_waiting_number", columnList = "status, waiting_number"),
                @Index(name = "idx_reservation_token_created", columnList = "status, created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reservation_token", columnNames = {"token"}),
                @UniqueConstraint(name = "uk_reservation_token_user_active",
                        columnNames = {"user_id", "status"})
        }
)
public class ReservationTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TokenStatus status;

    @Column(name = "waiting_number", nullable = true)
    private Long waitingNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // JPA용 기본 생성자
    protected ReservationTokenEntity() {}

    // 전체 생성자 (도메인에서 Entity 변환 시 사용)
    public ReservationTokenEntity(String token, Long userId, TokenStatus status,
                                  LocalDateTime createdAt, LocalDateTime expiresAt,
                                  Long waitingNumber) {
        this.token = token;
        this.userId = userId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.waitingNumber = waitingNumber;
        // createdAt, updatedAt은 @CreationTimestamp, @UpdateTimestamp가 자동 설정
        // 하지만 도메인에서 이미 설정된 경우 사용
        if (createdAt != null) {
            this.createdAt = createdAt;
        }
    }

    // 비즈니스 로직 메서드들
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == TokenStatus.ACTIVE && !isExpired();
    }

    public boolean isWaiting() {
        return status == TokenStatus.WAITING && !isExpired();
    }

    public boolean canActivate() {
        return status == TokenStatus.WAITING && !isExpired();
    }

    public boolean isValidForReservation() {
        return status == TokenStatus.ACTIVE && !isExpired();
    }

    // 상태 전환 메서드들
    public void activate() {
        if (!canActivate()) {
            throw new IllegalStateException("대기 상태의 유효한 토큰만 활성화할 수 있습니다.");
        }
        this.status = TokenStatus.ACTIVE;
        this.waitingNumber = null;  // 활성화되면 대기번호 제거
        // 활성화 시간 연장 (예: 10분)
        this.expiresAt = LocalDateTime.now().plusMinutes(10);
    }

    public void expire() {
        this.status = TokenStatus.EXPIRED;
    }

    public void use() {
        if (!isActive()) {
            throw new IllegalStateException("활성 상태의 토큰만 사용할 수 있습니다.");
        }
        this.status = TokenStatus.USED;
    }

    public void extendExpiration(int minutes) {
        this.expiresAt = this.expiresAt.plusMinutes(minutes);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public TokenStatus getStatus() { return status; }
    public void setStatus(TokenStatus status) { this.status = status; }

    public Long getWaitingNumber() { return waitingNumber; }
    public void setWaitingNumber(Long waitingNumber) { this.waitingNumber = waitingNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    // createdAt은 불변이므로 setter 제공하지 않음

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // updatedAt은 JPA가 자동 관리하므로 setter 제공하지 않음

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    // Object Methods
    @Override
    public String toString() {
        return String.format(
                "ReservationTokenEntity{id=%d, token='%s', userId=%d, status=%s, waitingNumber=%d, version=%d}",
                id, token != null ? token.substring(0, Math.min(token.length(), 8)) + "..." : null,
                userId, status, waitingNumber, version
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ReservationTokenEntity that = (ReservationTokenEntity) obj;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // 정적 팩토리 메서드들 (편의 메서드)
    public static ReservationTokenEntity createWaitingToken(String token, Long userId,
                                                            Long waitingNumber, LocalDateTime expiresAt) {
        return new ReservationTokenEntity(token, userId, TokenStatus.WAITING,
                LocalDateTime.now(), expiresAt, waitingNumber);
    }

    public static ReservationTokenEntity createActiveToken(String token, Long userId,
                                                           LocalDateTime expiresAt) {
        return new ReservationTokenEntity(token, userId, TokenStatus.ACTIVE,
                LocalDateTime.now(), expiresAt, null);
    }
}
