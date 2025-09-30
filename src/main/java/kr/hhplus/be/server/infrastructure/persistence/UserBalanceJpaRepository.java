package kr.hhplus.be.server.infrastructure.persistence;

import kr.hhplus.be.server.infrastructure.persistence.entity.BalanceHistoryEntity;
import kr.hhplus.be.server.infrastructure.persistence.entity.UserBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserBalanceJpaRepository extends JpaRepository<UserBalanceEntity, Long> {

    Optional<UserBalanceEntity> findByUserId(Long userId);

    // 비관적 락으로 잔액 조회 (동시성 제어)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.userId = :userId")
    Optional<UserBalanceEntity> findByUserIdWithLock(@Param("userId") Long userId);

    // 특정 금액 이상 보유한 사용자들 조회
    @Query("SELECT b FROM UserBalanceEntity b WHERE b.balance >= :amount ORDER BY b.balance DESC")
    List<UserBalanceEntity> findUsersWithBalanceGreaterThanEqual(@Param("amount") Long amount);

    // 전체 시스템 잔액 합계 (통계용)
    @Query("SELECT COALESCE(SUM(b.balance), 0) FROM UserBalanceEntity b")
    Long sumAllBalances();

    // 잔액이 0인 사용자 수 조회
    @Query("SELECT COUNT(b) FROM UserBalanceEntity b WHERE b.balance = 0")
    Long countUsersWithZeroBalance();

    /**
     * 조건부 UPDATE - 잔액 차감 (원자적 연산)
     * 현재 잔액이 차감할 금액 이상일 때만 차감 실행
     * @return 업데이트된 행 수 (성공 시 1, 실패 시 0)
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity u " +
            "SET u.balance = u.balance - :amount, u.lastUpdatedAt = :now, u.version = u.version + 1 " +
            "WHERE u.userId = :userId AND u.balance >= :amount")
    int deductBalanceConditionally(@Param("userId") Long userId,
                                   @Param("amount") Long amount,
                                   @Param("now") LocalDateTime now);

    /**
     * 조건부 UPDATE - 잔액 충전 (원자적 연산)
     * @return 업데이트된 행 수 (성공 시 1, 실패 시 0)
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity u " +
            "SET u.balance = u.balance + :amount, u.lastUpdatedAt = :now, u.version = u.version + 1 " +
            "WHERE u.userId = :userId")
    int chargeBalanceConditionally(@Param("userId") Long userId,
                                   @Param("amount") Long amount,
                                   @Param("now") LocalDateTime now);

    /**
     * 낙관적 락 기반 UPDATE - 버전 체크와 함께 잔액 차감
     * @return 업데이트된 행 수 (성공 시 1, 버전 충돌 시 0)
     */
    @Modifying
    @Query("UPDATE UserBalanceEntity u " +
            "SET u.balance = u.balance - :amount, u.lastUpdatedAt = :now, u.version = u.version + 1 " +
            "WHERE u.userId = :userId AND u.balance >= :amount AND u.version = :expectedVersion")
    int deductBalanceWithOptimisticLock(@Param("userId") Long userId,
                                        @Param("amount") Long amount,
                                        @Param("expectedVersion") Long expectedVersion,
                                        @Param("now") LocalDateTime now);

    /**
     * 잔액 설정 (초기 잔액 생성용)
     * INSERT 시 중복 방지 로직과 함께 사용
     */
    @Modifying
    @Query("INSERT INTO UserBalanceEntity (userId, balance, lastUpdatedAt, version, createdAt) " +
            "VALUES (:userId, :balance, :now, 0, :now)")
    int createInitialBalance(@Param("userId") Long userId,
                             @Param("balance") Long balance,
                             @Param("now") LocalDateTime now);

}
