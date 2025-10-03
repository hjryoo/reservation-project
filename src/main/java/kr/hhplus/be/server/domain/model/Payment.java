package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;

public class Payment {
    // ===== 기술적 필드 (Infrastructure에서 관리) =====
    private Long id;                    // mutable - DB에서 자동 생성
    private LocalDateTime createdAt;    // mutable - DB에서 자동 생성
    private LocalDateTime updatedAt;    // mutable - DB에서 자동 업데이트
    private Long version;               // mutable - 낙관적 락용

    // ===== 비즈니스 필드 (불변성 보장) =====
    private final Long reservationId;
    private final Long userId;
    private final Long amount;
    private final PaymentStatus status;
    private final LocalDateTime paidAt;
    private final String idempotencyKey;
    private final String paymentMethod;
    private final String transactionId;
    private final String failureReason;

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED
    }

    // ===== 메인 생성자 =====
    private Payment(Long reservationId, Long userId, Long amount,
                    PaymentStatus status, LocalDateTime paidAt, String idempotencyKey,
                    String paymentMethod, String transactionId, String failureReason) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
        this.idempotencyKey = idempotencyKey;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.failureReason = failureReason;
        // 기술적 필드는 null로 초기화 (Infrastructure에서 설정)
        this.id = null;
        this.createdAt = null;
        this.updatedAt = null;
        this.version = null;
    }

    // ===== 팩토리 메서드들 =====
    public static Payment create(Long reservationId, Long userId, Long amount) {
        return new Payment(
                reservationId, userId, amount,
                PaymentStatus.PENDING, LocalDateTime.now(),
                null, "DEFAULT", null, null
        );
    }

    public static Payment createPending(Long userId, Long amount,
                                        String paymentMethod, String idempotencyKey) {
        return new Payment(
                null, userId, amount,
                PaymentStatus.PENDING, LocalDateTime.now(),
                idempotencyKey, paymentMethod, null, null
        );
    }

    public static Payment createWithReservation(Long reservationId, Long userId, Long amount,
                                                String paymentMethod, String idempotencyKey) {
        return new Payment(
                reservationId, userId, amount,
                PaymentStatus.PENDING, LocalDateTime.now(),
                idempotencyKey, paymentMethod, null, null
        );
    }

    public static Payment createWithIdempotency(Long reservationId, Long userId, Long amount, String idempotencyKey) {
        return new Payment(
                reservationId, userId, amount,
                PaymentStatus.PENDING, LocalDateTime.now(),
                idempotencyKey, "DEFAULT", null, null
        );
    }

    // ===== 비즈니스 규칙: 결제 완료 =====
    public Payment complete() {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 결제만 완료할 수 있습니다.");
        }

        Payment completed = new Payment(
                this.reservationId, this.userId, this.amount,
                PaymentStatus.COMPLETED, LocalDateTime.now(),
                this.idempotencyKey, this.paymentMethod, null, null
        );

        // 기술적 필드 복사
        completed.id = this.id;
        completed.createdAt = this.createdAt;
        completed.version = this.version;

        return completed;
    }

    public Payment complete(String transactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 결제만 완료할 수 있습니다.");
        }

        Payment completed = new Payment(
                this.reservationId, this.userId, this.amount,
                PaymentStatus.COMPLETED, LocalDateTime.now(),
                this.idempotencyKey, this.paymentMethod, transactionId, null
        );

        // 기술적 필드 복사
        completed.id = this.id;
        completed.createdAt = this.createdAt;
        completed.version = this.version;

        return completed;
    }

    public Payment fail(String failureReason) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 결제만 실패 처리할 수 있습니다.");
        }

        Payment failed = new Payment(
                this.reservationId, this.userId, this.amount,
                PaymentStatus.FAILED, this.paidAt,
                this.idempotencyKey, this.paymentMethod, this.transactionId, failureReason
        );

        // 기술적 필드 복사
        failed.id = this.id;
        failed.createdAt = this.createdAt;
        failed.version = this.version;

        return failed;
    }

    // ===== Infrastructure 계층 전용 메서드 =====
    /**
     * Infrastructure 계층에서만 호출하는 ID 할당 메서드
     * 도메인 로직에서는 절대 호출하지 않음
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID가 이미 할당되어 있습니다: " + this.id);
        }
        this.id = id;
    }

    /**
     * Infrastructure 계층에서만 호출하는 기술적 필드 설정
     */
    public void assignTechnicalFields(Long id, LocalDateTime createdAt, LocalDateTime updatedAt, Long version) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    // ===== 비즈니스 규칙 검증 메서드 =====
    public boolean isPending() { return this.status == PaymentStatus.PENDING; }
    public boolean isCompleted() { return this.status == PaymentStatus.COMPLETED; }
    public boolean isFailed() { return this.status == PaymentStatus.FAILED; }
    public boolean canBeCompleted() { return this.status == PaymentStatus.PENDING; }
    public boolean canBeRetried() { return this.status == PaymentStatus.FAILED; }

    // ===== Getters =====
    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public Long getUserId() { return userId; }
    public Long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTransactionId() { return transactionId; }
    public String getFailureReason() { return failureReason; }

    // 기술적 필드 Getters
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    @Override
    public String toString() {
        return String.format(
                "Payment{id=%d, reservationId=%d, userId=%d, amount=%d, status=%s, idempotencyKey='%s'}",
                id, reservationId, userId, amount, status, idempotencyKey
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Payment payment = (Payment) obj;
        return id != null && id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}