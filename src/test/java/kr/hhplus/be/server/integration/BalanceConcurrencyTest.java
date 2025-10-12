package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.ConcurrencyUserBalanceService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.domain.model.UserBalance;
import kr.hhplus.be.server.domain.repository.UserBalanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Redis 분산락을 적용한 UserBalance 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(RedisTestContainerConfig.class)
public class BalanceConcurrencyTest {

    @Autowired
    private ConcurrencyUserBalanceService concurrencyUserBalanceService;

    @Autowired
    private UserBalanceRepository userBalanceRepository;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        userBalanceRepository.createInitialBalanceIfNotExists(testUserId, 0L);
    }

    @AfterEach
    void tearDown() {
        // 테스트 데이터 정리
        Optional<UserBalance> balance = userBalanceRepository.findByUserId(testUserId);
        balance.ifPresent(b -> {
            // 실제 삭제 로직 필요 시 추가
        });
    }

    @Test
    @DisplayName("분산락: 동시 충전/차감 혼합 테스트")
    void mixedOperationsWithDistributedLockTest() throws InterruptedException {
        // Given
        int chargeThreads = 10;
        int deductThreads = 5;
        int totalThreads = chargeThreads + deductThreads;

        Long chargeAmount = 1000L;
        Long deductAmount = 500L;

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(totalThreads);

        AtomicInteger chargeSuccess = new AtomicInteger(0);
        AtomicInteger deductSuccess = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        // When: 충전 스레드
        for (int i = 0; i < chargeThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    concurrencyUserBalanceService.chargeBalance(testUserId, chargeAmount);
                    chargeSuccess.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // 차감 스레드
        for (int i = 0; i < deductThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    concurrencyUserBalanceService.deductBalanceWithConditionalUpdate(testUserId, deductAmount);
                    deductSuccess.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        System.out.println("=== 분산락 혼합 작업 테스트 결과 ===");
        System.out.println("충전 성공: " + chargeSuccess.get() + "건");
        System.out.println("차감 성공: " + deductSuccess.get() + "건");
        System.out.println("실패: " + failures.get() + "건");

        assertThat(chargeSuccess.get()).isEqualTo(chargeThreads);

        UserBalance finalBalance = concurrencyUserBalanceService.getBalance(testUserId);
        Long expectedBalance = (chargeAmount * chargeSuccess.get()) - (deductAmount * deductSuccess.get());

        assertThat(finalBalance.getBalance()).isEqualTo(expectedBalance);

        System.out.println("최종 잔액: " + finalBalance.getBalance());
        System.out.println("예상 잔액: " + expectedBalance);
    }
}