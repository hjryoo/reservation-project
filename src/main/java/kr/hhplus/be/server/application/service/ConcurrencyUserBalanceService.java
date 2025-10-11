package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.UserBalance;
import kr.hhplus.be.server.domain.repository.UserBalanceRepository;
import kr.hhplus.be.server.infrastructure.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserBalance 동시성 처리 서비스
 *
 */
@Service("concurrencyUserBalanceService")
@RequiredArgsConstructor
public class ConcurrencyUserBalanceService {

    private final UserBalanceRepository userBalanceRepository;

    /**
     * 잔액 차감
     *
     * 락 키: balance:operation:{userId}
     * 락 범위: 잔액 조회 → 차감 가능 여부 확인 → 차감 → 저장
     */
    @DistributedLock(
            key = "'balance:operation:' + #userId",
            waitTime = 5L,
            leaseTime = 3L
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserBalance deductBalanceWithConditionalUpdate(Long userId, Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }

        // 1. 잔액 조회
        UserBalance currentBalance = userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("잔액 정보를 찾을 수 없습니다."));

        // 2. 차감 가능 여부 확인
        if (!currentBalance.canDeduct(amount)) {
            throw new IllegalStateException(
                    String.format("잔액이 부족합니다. 현재 잔액: %d, 차감 요청: %d",
                            currentBalance.getBalance(), amount)
            );
        }

        // 3. 잔액 차감
        UserBalance updatedBalance = currentBalance.deduct(amount);

        // 4. 저장
        return userBalanceRepository.save(updatedBalance);
    }

    /**
     * 잔액 충전 (분산락 적용)
     *
     * 락 키: balance:operation:{userId}  ⭐ deduct와 동일한 락 키
     */
    @DistributedLock(
            key = "'balance:operation:' + #userId",
            waitTime = 5L,
            leaseTime = 3L
    )
    @Transactional
    public UserBalance chargeBalance(Long userId, Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        // 계정이 없으면 생성
        if (userBalanceRepository.findByUserId(userId).isEmpty()) {
            userBalanceRepository.createInitialBalanceIfNotExists(userId, 0L);
        }

        // 1. 잔액 조회
        UserBalance currentBalance = userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("잔액 정보를 찾을 수 없습니다."));

        // 2. 잔액 충전
        UserBalance updatedBalance = currentBalance.charge(amount);

        // 3. 저장
        return userBalanceRepository.save(updatedBalance);
    }

    /**
     * 잔액 조회 (락 불필요 - 읽기 전용)
     */
    @Transactional(readOnly = true)
    public UserBalance getBalance(Long userId) {
        return userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("잔액 정보를 찾을 수 없습니다."));
    }

    /**
     * 초기 잔액 생성 (분산락 적용)
     *
     * 락 키: balance:create:{userId}
     */
    @DistributedLock(
            key = "'balance:create:' + #userId",
            waitTime = 5L,
            leaseTime = 3L
    )
    @Transactional
    public UserBalance createInitialBalance(Long userId, Long initialAmount) {
        if (initialAmount < 0) {
            throw new IllegalArgumentException("초기 금액은 0 이상이어야 합니다.");
        }

        boolean created = userBalanceRepository.createInitialBalanceIfNotExists(userId, initialAmount);

        if (!created) {
            throw new IllegalStateException("이미 잔액 정보가 존재합니다.");
        }

        return userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("잔액 생성에 실패했습니다."));
    }
}