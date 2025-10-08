package kr.hhplus.be.server.infrastructure.persistence.entity;

import kr.hhplus.be.server.domain.model.Payment.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        indexes = {
                // 성능 최적화를 위한 복합 인덱스
                @Index(name = "idx_payment_user_status", columnList = "user_id, status"),
                @Index(name = "idx_payment_reservation_user", columnList = "reservation_id, user_id"),
                @Index(name = "idx_payment_status_created", columnList = "status, created_at"),
                @Index(name = "idx_payment_created_at", columnList = "created_at"),
                @Index(name = "idx_payment_idempotency", columnList = "idempotency_key"),
                // 만료된 결제 조회용
                @Index(name = "idx_payment_status_updated", columnList = "status, updated_at")
        },
        uniqueConstraints = {
                // 멱등성 보장을 위한 UNIQUE 제약
                @UniqueConstraint(name = "uk_payment_idempotency", columnNames = {"idempotency_key"}),
                // 사용자별 멱등성 키 중복 방지 (더 엄격한 제약)
                @UniqueConstraint(name = "uk_payment_user_idempotency", columnNames = {"user_id", "idempotency_key"})
        }
)
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = true) // null 허용 (예약 없는 충전 등)
    private Long reservationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "idempotency_key", unique = true, length = 100, nullable = false)
    private String idempotencyKey;

    @Column(name = "payment_method", length = 50, nullable = false)
    private String paymentMethod;

    @Column(name = "transaction_id", length = 100, nullable = true) // 완료 후에만 값 존재
    private String transactionId;

    @Column(name = "failure_reason", length = 500, nullable = true) // 실패 시에만 값 존재
    private String failureReason;

    // ===== 기술적 필드 (감사 추적 및 동시성 제어) =====

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "paid_at", nullable = true) // 결제 완료 시점
    private LocalDateTime paidAt;

    /**
     * 낙관적 락을 위한 버전 필드
     * 동시성 제어 및 무결성 보장
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // ===== 생성자 =====

    /**
     * JPA용 기본 생성자 (protected)
     */
    protected PaymentEntity() {}

    /**
     * 도메인 → Entity 변환용 생성자
     */
    public PaymentEntity(Long reservationId, Long userId, Long amount,
                         PaymentStatus status, String idempotencyKey,
                         String paymentMethod, String transactionId,
                         String failureReason, LocalDateTime paidAt) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.failureReason = failureReason;
        this.paidAt = paidAt;
        // version은 JPA가 자동 관리
        // createdAt, updatedAt은 @CreationTimestamp, @UpdateTimestamp가 자동 설정
    }

    // ===== 비즈니스 규칙 검증 메서드 =====

    /**
     * 결제 완료 처리 가능 여부 확인
     */
    public boolean canBeCompleted() {
        return this.status == PaymentStatus.PENDING;
    }

    /**
     * 결제 실패 처리 가능 여부 확인
     */
    public boolean canBeFailed() {
        return this.status == PaymentStatus.PENDING;
    }

    /**
     * 결제 재시도 가능 여부 확인
     */
    public boolean canBeRetried() {
        return this.status == PaymentStatus.FAILED;
    }

    // ===== Getters and Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    // createdAt은 불변이므로 setter 제공하지 않음

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // updatedAt은 JPA가 자동 관리하므로 setter 제공하지 않음

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    // ===== Object Methods =====

    @Override
    public String toString() {
        return String.format(
                "PaymentEntity{id=%d, userId=%d, amount=%d, status=%s, idempotencyKey='%s', version=%d}",
                id, userId, amount, status, idempotencyKey, version
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        PaymentEntity that = (PaymentEntity) obj;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
