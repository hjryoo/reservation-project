package kr.hhplus.be.server.domain.model;

import java.time.LocalDateTime;

public class UserBalance {
    private Long id;
    private final Long userId;
    private final Long balance;
    private final LocalDateTime lastUpdatedAt;
    private final Long version; // 낙관적 락을 위한 버전
    private final LocalDateTime createdAt;

    // 모든 필드를 포함한 생성자
    private UserBalance(Long userId, Long balance, LocalDateTime lastUpdatedAt, Long version, LocalDateTime createdAt) {
        if (balance < 0) {
            throw new IllegalArgumentException("잔액은 음수가 될 수 없습니다.");
        }
        this.userId = userId;
        this.balance = balance;
        this.lastUpdatedAt = lastUpdatedAt;
        this.version = version;
        this.createdAt = createdAt;
    }

    // ID 포함 생성자
    private UserBalance(Long userId, Long balance, LocalDateTime lastUpdatedAt, Long version, LocalDateTime createdAt, Long id) {
        if (balance < 0) {
            throw new IllegalArgumentException("잔액은 음수가 될 수 없습니다.");
        }
        this.id = id;
        this.userId = userId;
        this.balance = balance;
        this.lastUpdatedAt = lastUpdatedAt;
        this.version = version;
        this.createdAt = createdAt;
    }

    // === 팩토리 메서드들 ===

    public static UserBalance create(Long userId, Long initialBalance) {
        LocalDateTime now = LocalDateTime.now();
        return new UserBalance(userId, initialBalance, now, 0L, now);
    }

    public static UserBalance createWithDetails(Long userId, Long balance, LocalDateTime lastUpdatedAt,
                                                Long version, LocalDateTime createdAt, Long id) {
        return new UserBalance(userId, balance, lastUpdatedAt, version, createdAt, id);
    }

    public static UserBalance createNew(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return new UserBalance(userId, 0L, now, 0L, now);
    }

    public static UserBalance of(Long userId, Long balance, LocalDateTime lastUpdatedAt, Long version) {
        // createdAt을 lastUpdatedAt으로 임시 설정 (Infrastructure에서 올바른 값으로 교체)
        return new UserBalance(userId, balance, lastUpdatedAt, version, lastUpdatedAt);
    }

    // === 비즈니스 로직 ===

    public UserBalance charge(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        Long newBalance = this.balance + amount;
        return new UserBalance(this.userId, newBalance, LocalDateTime.now(), this.version + 1, this.createdAt, this.id);
    }

    public UserBalance deduct(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }

        if (this.balance < amount) {
            throw new IllegalArgumentException("잔액이 부족합니다. 현재 잔액: " + this.balance + ", 차감 요청: " + amount);
        }

        Long newBalance = this.balance - amount;
        return new UserBalance(this.userId, newBalance, LocalDateTime.now(), this.version + 1, this.createdAt, this.id);
    }

    public UserBalance deductBalance(Long amount) {
        return deduct(amount); // 기존 deduct 메서드 재사용
    }

    public UserBalance chargeBalance(Long amount) {
        return charge(amount); // 기존 charge 메서드 재사용
    }

    public boolean canDeduct(Long amount) {
        return this.balance >= amount;
    }

    public void assignId(Long id) {
        this.id = id;
    }

    // === Getters ===
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getBalance() { return balance; }
    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
