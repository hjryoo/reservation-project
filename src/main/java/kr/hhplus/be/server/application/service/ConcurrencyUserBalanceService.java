package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.UserBalance;
import kr.hhplus.be.server.domain.repository.UserBalanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service("concurrencyUserBalanceService")
public class ConcurrencyUserBalanceService {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyUserBalanceService.class);

    private final UserBalanceRepository userBalanceRepository;

    public ConcurrencyUserBalanceService(UserBalanceRepository userBalanceRepository) {
        this.userBalanceRepository = userBalanceRepository;
    }

    /**
     * 전략 1: 조건부 UPDATE를 사용한 잔액 차감 (가장 안전하고 빠름)
     * Race Condition 완전 방지, 락 대기 없음
     */
    @Transactional
    public UserBalance deductBalanceWithConditionalUpdate(Long userId, Long amount) {
        // 파라미터 검증
        if (amount <= 0) {
            throw new IllegalArgumentException("차감할 금액은 0보다 커야 합니다.");
        }

        // 원자적 연산으로 잔액 차감 시도
        boolean success = userBalanceRepository.deductBalanceConditionally(userId, amount);

        if (!success) {
            // 실패 원인 확인을 위해 현재 잔액 조회
            Optional<UserBalance> currentBalance = userBalanceRepository.findByUserId(userId);
            if (currentBalance.isEmpty()) {
                throw new IllegalArgumentException("사용자 잔액 정보를 찾을 수 없습니다.");
            } else {
                throw new IllegalStateException("잔액이 부족합니다. 현재 잔액: " +
                        currentBalance.get().getBalance() + ", 차감 요청: " + amount);
            }
        }

        // 차감 성공 후 최신 잔액 정보 조회
        return userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("차감 후 잔액 정보를 조회할 수 없습니다."));
    }

    /**
     * 전략 2: 비관적 락을 사용한 잔액 차감 (락 대기 발생 가능)
     * 높은 동시성에서는 성능 저하 가능하지만 확실한 동시성 제어
     */
    @Transactional
    public UserBalance deductBalanceWithPessimisticLock(Long userId, Long amount) {
        // 파라미터 검증
        if (amount <= 0) {
            throw new IllegalArgumentException("차감할 금액은 0보다 커야 합니다.");
        }

        // 비관적 락으로 잔액 정보 조회
        UserBalance currentBalance = userBalanceRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 잔액 정보를 찾을 수 없습니다."));

        // 잔액 검증 및 차감
        UserBalance updatedBalance = currentBalance.deduct(amount);

        // 저장 후 반환
        return userBalanceRepository.save(updatedBalance);
    }

    /**
     * 전략 3: 낙관적 락을 사용한 잔액 차감 (지수 백오프 재시도 로직 포함)
     * 충돌률이 낮을 때 효과적
     *
     * 재시도는 트랜잭션 밖에서 수행하여 DB 커넥션 점유 시간 최소화
     */
    public UserBalance deductBalanceWithOptimisticLock(Long userId, Long amount) {
        return deductBalanceWithOptimisticLockRetry(userId, amount, 5);
    }

    /**
     * 낙관적 락 재시도 로직 (트랜잭션 밖에서 실행)
     * 각 시도마다 새로운 트랜잭션 생성
     */
    private UserBalance deductBalanceWithOptimisticLockRetry(Long userId, Long amount, int maxRetries) {
        // 파라미터 검증
        if (amount <= 0) {
            throw new IllegalArgumentException("차감할 금액은 0보다 커야 합니다.");
        }

        int baseDelayMs = 1;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // 각 시도마다 새로운 트랜잭션으로 실행
                return attemptDeductBalance(userId, amount, attempt);

            } catch (OptimisticLockException e) {
                // 낙관적 락 충돌 발생
                if (attempt < maxRetries - 1) {
                    long delayMs = calculateExponentialBackoff(baseDelayMs, attempt);
                    logger.debug("낙관적 락 충돌 발생. userId: {}, attempt: {}, 대기 시간: {}ms",
                            userId, attempt + 1, delayMs);

                    // LockSupport.parkNanos 사용 (Thread.sleep 대체)
                    parkNanos(TimeUnit.MILLISECONDS.toNanos(delayMs));
                } else {
                    logger.warn("낙관적 락 재시도 횟수 초과. userId: {}, maxRetries: {}", userId, maxRetries);
                    throw new RuntimeException("낙관적 락 재시도 횟수 초과. 동시성이 너무 높습니다.");
                }
            } catch (IllegalStateException e) {
                // 잔액 부족 등의 비즈니스 예외는 즉시 전파
                throw e;
            }
        }

        throw new RuntimeException("낙관적 락 재시도 횟수 초과.");
    }

    /**
     * 단일 트랜잭션 내에서 낙관적 락 차감 시도
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserBalance attemptDeductBalance(Long userId, Long amount, int attempt) {
        // 현재 잔액 정보 조회
        UserBalance currentBalance = userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 잔액 정보를 찾을 수 없습니다."));

        // 잔액 부족 체크
        if (!currentBalance.canDeduct(amount)) {
            throw new IllegalStateException("잔액이 부족합니다. 현재 잔액: " +
                    currentBalance.getBalance() + ", 차감 요청: " + amount);
        }

        // 낙관적 락으로 차감 시도
        boolean success = userBalanceRepository.deductBalanceWithOptimisticLock(
                userId, amount, currentBalance.getVersion()
        );

        if (!success) {
            throw new OptimisticLockException("낙관적 락 충돌");
        }

        // 성공 시 최신 정보 반환
        logger.debug("낙관적 락 차감 성공. userId: {}, attempt: {}", userId, attempt + 1);
        return userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("차감 후 잔액 정보를 조회할 수 없습니다."));
    }

    /**
     * Thread.sleep() 대신 LockSupport.parkNanos() 사용
     * 더 정확한 대기 시간, 인터럽트 처리 불필요
     */
    private void parkNanos(long nanos) {
        if (nanos > 0) {
            java.util.concurrent.locks.LockSupport.parkNanos(nanos);
        }
    }

    /**
     * 지수 백오프 대기 시간 계산
     */
    private long calculateExponentialBackoff(int baseDelayMs, int attempt) {
        long delayMs = baseDelayMs * (1L << attempt);
        long maxDelayMs = 100;
        return Math.min(delayMs, maxDelayMs);
    }

    /**
     * 잔액 충전 (조건부 UPDATE 사용)
     */
    @Transactional
    public UserBalance chargeBalance(Long userId, Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전할 금액은 0보다 커야 합니다.");
        }

        if (userBalanceRepository.findByUserId(userId).isEmpty()) {
            userBalanceRepository.createInitialBalanceIfNotExists(userId, 0L);
        }

        boolean success = userBalanceRepository.chargeBalanceConditionally(userId, amount);

        if (!success) {
            throw new IllegalArgumentException("사용자 잔액 정보를 찾을 수 없습니다.");
        }

        return userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("충전 후 잔액 정보를 조회할 수 없습니다."));
    }

    public UserBalance getBalance(Long userId) {
        return userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 잔액 정보를 찾을 수 없습니다."));
    }

    @Transactional
    public UserBalance createInitialBalance(Long userId, Long initialAmount) {
        if (initialAmount < 0) {
            throw new IllegalArgumentException("초기 잔액은 음수가 될 수 없습니다.");
        }

        boolean created = userBalanceRepository.createInitialBalanceIfNotExists(userId, initialAmount);

        if (!created) {
            throw new IllegalStateException("이미 존재하는 사용자 잔액입니다.");
        }

        return userBalanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("생성된 잔액 정보를 조회할 수 없습니다."));
    }

    /**
     * 낙관적 락 예외 (내부 사용)
     */
    private static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}