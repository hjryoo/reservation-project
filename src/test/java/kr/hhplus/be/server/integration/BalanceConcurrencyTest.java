package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.domain.model.UserBalance;
import kr.hhplus.be.server.domain.repository.UserBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class BalanceConcurrencyTest {

    @Autowired
    private ConcurrencyUserBalanceService concurrencyUserBalanceService;

    @Autowired
    private UserBalanceRepository userBalanceRepository;

    private static final Long USER_ID = 1L;
    private static final Long USER_ID_2 = 999L;
    private static final Long USER_ID_3 = 1000L;
    private static final Long INITIAL_BALANCE = 1000000L; // 100만원
    private static final Long DEDUCT_AMOUNT = 10000L;     // 1만원

    @BeforeEach
    void setUp() {
        // 테스트 전 기존 데이터 정리
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리
        cleanupTestData();
    }

    private void cleanupTestData() {
        // H2 데이터베이스 초기화 또는 테스트 데이터 삭제
        try {
            userBalanceRepository.findByUserId(USER_ID).ifPresent(balance -> {
                // 삭제 로직이 있다면 여기서 처리
            });
            userBalanceRepository.findByUserId(USER_ID_2).ifPresent(balance -> {
                // 삭제 로직이 있다면 여기서 처리
            });
            userBalanceRepository.findByUserId(USER_ID_3).ifPresent(balance -> {
                // 삭제 로직이 있다면 여기서 처리
            });
        } catch (Exception e) {
            // 데이터가 없으면 무시
        }
    }

    @Test
    @DisplayName("동시성 테스트: 100명이 동시에 잔액 차감 시도, 정확한 계산")
    void concurrentBalanceDeductionTest() throws InterruptedException {
        // Given: 테스트용 사용자 잔액 생성
        createOrUpdateUserBalance(USER_ID, INITIAL_BALANCE);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 100개 스레드가 동시에 1만원씩 차감 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    concurrencyUserBalanceService.deductBalanceWithConditionalUpdate(USER_ID, DEDUCT_AMOUNT);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 성공 횟수만큼 정확히 차감되어야 함
        UserBalance finalBalance = concurrencyUserBalanceService.getBalance(USER_ID);
        Long expectedBalance = INITIAL_BALANCE - (successCount.get() * DEDUCT_AMOUNT);

        assertThat(finalBalance.getBalance()).isEqualTo(expectedBalance);
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);

        System.out.println("=== 동시 차감 테스트 결과 ===");
        System.out.println("성공한 차감: " + successCount.get());
        System.out.println("실패한 차감: " + failureCount.get());
        System.out.println("최종 잔액: " + finalBalance.getBalance());
    }

    @Test
    @DisplayName("Race Condition 방지 테스트: 정확히 잔액만큼만 차감")
    void preventOverDeductionTest() throws InterruptedException {
        // Given: 잔액 5만원, 10명이 1만원씩 차감 시도 (총 10만원 요청)
        Long limitedBalance = 50000L;
        createOrUpdateUserBalance(USER_ID_2, limitedBalance);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 1만원씩 차감 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    concurrencyUserBalanceService.deductBalanceWithConditionalUpdate(USER_ID_2, DEDUCT_AMOUNT);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 잔액 부족 예외는 정상
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 5명만 성공해야 하고, 잔액은 0이 되어야 함
        assertThat(successCount.get()).isEqualTo(5);

        UserBalance finalBalance = concurrencyUserBalanceService.getBalance(USER_ID_2);
        assertThat(finalBalance.getBalance()).isEqualTo(0L);

        System.out.println("=== Race Condition 방지 테스트 결과 ===");
        System.out.println("성공한 차감: " + successCount.get() + "명 (예상: 5명)");
        System.out.println("최종 잔액: " + finalBalance.getBalance() + "원 (예상: 0원)");
    }

    @Test
    @DisplayName("낙관적 락 재시도 테스트")
    void optimisticLockRetryTest() throws InterruptedException {
        // Given: 테스트용 사용자 잔액 생성
        createOrUpdateUserBalance(USER_ID_3, INITIAL_BALANCE);

        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 낙관적 락 방식으로 동시 차감 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    concurrencyUserBalanceService.deductBalanceWithOptimisticLock(USER_ID_3, DEDUCT_AMOUNT);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 모든 요청이 처리되어야 함 (재시도로 인해)
        UserBalance finalBalance = concurrencyUserBalanceService.getBalance(USER_ID_3);
        Long expectedBalance = INITIAL_BALANCE - (successCount.get() * DEDUCT_AMOUNT);

        assertThat(finalBalance.getBalance()).isEqualTo(expectedBalance);
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);

        System.out.println("=== 낙관적 락 재시도 테스트 결과 ===");
        System.out.println("낙관적 락 성공: " + successCount.get());
        System.out.println("낙관적 락 실패: " + failureCount.get());
        System.out.println("최종 잔액: " + finalBalance.getBalance());
    }

    /**
     * 사용자 잔액을 생성하거나 업데이트하는 헬퍼 메서드
     */
    private void createOrUpdateUserBalance(Long userId, Long balance) {
        Optional<UserBalance> existingBalance = userBalanceRepository.findByUserId(userId);

        if (existingBalance.isEmpty()) {
            // 새로 생성
            concurrencyUserBalanceService.createInitialBalance(userId, balance);
        } else {
            // 기존 잔액이 있으면 충전/차감으로 조정
            UserBalance current = existingBalance.get();
            Long currentBalance = current.getBalance();

            if (currentBalance < balance) {
                // 부족하면 충전
                concurrencyUserBalanceService.chargeBalance(userId, balance - currentBalance);
            } else if (currentBalance > balance) {
                // 많으면 차감
                concurrencyUserBalanceService.deductBalanceWithConditionalUpdate(userId, currentBalance - balance);
            }
            // 같으면 아무것도 안 함
        }
    }
}
