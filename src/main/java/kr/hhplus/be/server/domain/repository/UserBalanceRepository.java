package kr.hhplus.be.server.domain.repository;

import kr.hhplus.be.server.domain.model.UserBalance;
import kr.hhplus.be.server.domain.model.BalanceHistory;
import kr.hhplus.be.server.domain.model.TransactionType;
import java.util.List;
import java.util.Optional;

public interface UserBalanceRepository {
    UserBalance save(UserBalance balance);
    Optional<UserBalance> findByUserId(Long userId);

    // 동시성 제어를 위한 비관적 락
    Optional<UserBalance> findByUserIdWithLock(Long userId);

    // 잔액 히스토리 관리
    BalanceHistory saveHistory(BalanceHistory history);
    List<BalanceHistory> findHistoriesByUserId(Long userId);
    List<BalanceHistory> findHistoriesByUserIdAndType(Long userId, TransactionType type);

    // 멱등성 보장을 위한 중복 체크
    boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    Optional<BalanceHistory> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    // 통계 및 관리
    List<UserBalance> findUsersWithBalanceGreaterThan(Long amount);

    Long getTotalSystemBalance();

    /**
     * 조건부 잔액 차감 (원자적 연산)
     * @return true: 차감 성공, false: 잔액 부족으로 실패
     */
    boolean deductBalanceConditionally(Long userId, Long amount);

    /**
     * 조건부 잔액 충전 (원자적 연산)
     * @return true: 충전 성공, false: 사용자 없음으로 실패
     */
    boolean chargeBalanceConditionally(Long userId, Long amount);

    /**
     * 낙관적 락 기반 잔액 차감
     * @return true: 차감 성공, false: 버전 충돌 또는 잔액 부족으로 실패
     */
    boolean deductBalanceWithOptimisticLock(Long userId, Long amount, Long expectedVersion);

    /**
     * 사용자 잔액 초기 생성 (중복 방지)
     * @return true: 생성 성공, false: 이미 존재함으로 실패
     */
    boolean createInitialBalanceIfNotExists(Long userId, Long initialBalance);
}
