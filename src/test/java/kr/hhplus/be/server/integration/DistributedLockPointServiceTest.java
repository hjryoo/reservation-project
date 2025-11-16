package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.PointService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.config.TestEventConfig;
import kr.hhplus.be.server.domain.entity.Point;
import kr.hhplus.be.server.domain.repository.PointHistoryRepository;
import kr.hhplus.be.server.domain.repository.PointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Redis 분산락을 적용한 포인트 충전/사용 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({RedisTestContainerConfig.class, TestEventConfig.class})
public class DistributedLockPointServiceTest {

    @Autowired private PointService pointService;
    @Autowired private PointRepository pointRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;

    private Long testUserId;
    private BigDecimal initialBalance;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        initialBalance = new BigDecimal("10000");

        Point initialPoint = Point.create(testUserId);
        initialPoint.charge(initialBalance);
        pointRepository.save(initialPoint);
    }

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        pointRepository.deleteAll();
    }

    @Test
    @DisplayName("분산락 포인트 충전: 100명이 동시 충전, 정확한 최종 잔액 보장")
    void shouldHandleConcurrentChargesWithDistributedLock() throws InterruptedException {
        // Given
        int threadCount = 100;
        BigDecimal chargeAmount = new BigDecimal("1000");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.chargePoint(testUserId, chargeAmount, "동시 충전 테스트 " + index);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("충전 실패 [" + index + "]: " + e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        System.out.println("=== Redis 분산락 포인트 충전 테스트 결과 ===");
        System.out.println("충전 성공: " + successCount.get() + "건");
        System.out.println("충전 실패: " + failureCount.get() + "건");

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failureCount.get()).isZero();

        Point finalPoint = pointService.getPointBalance(testUserId);
        BigDecimal expectedBalance = initialBalance.add(chargeAmount.multiply(new BigDecimal(threadCount)));

        // ✅ BigDecimal 비교는 반드시 compareTo 사용
        assertThat(finalPoint.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(expectedBalance);

        System.out.println("최종 잔액: " + finalPoint.getBalance());
        System.out.println("예상 잔액: " + expectedBalance);
    }

    @Test
    @DisplayName("분산락 포인트 사용: 20명이 동시 사용, 잔액 부족 시 실패")
    void shouldHandleConcurrentUsagesWithDistributedLock() throws InterruptedException {
        // Given
        int threadCount = 20;
        BigDecimal useAmount = new BigDecimal("1000");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.usePoint(testUserId, useAmount, "동시 사용 테스트 " + index);
                    successCount.incrementAndGet();
                    System.out.println("✅ 사용 성공 [" + index + "]");
                } catch (IllegalArgumentException e) {
                    failureCount.incrementAndGet();
                    System.out.println("❌ 사용 실패 [" + index + "]: 잔액 부족");
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("⚠️ 사용 실패 [" + index + "]: " + e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        System.out.println("\n=== Redis 분산락 포인트 사용 테스트 결과 ===");
        System.out.println("사용 성공: " + successCount.get() + "건");
        System.out.println("사용 실패: " + failureCount.get() + "건");

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failureCount.get()).isEqualTo(10);

        Point finalPoint = pointService.getPointBalance(testUserId);
        assertThat(finalPoint.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("분산락 동시 충전/사용: 혼합 작업 시 정확한 잔액 계산")
    void shouldHandleMixedOperationsWithDistributedLock() throws InterruptedException {
        // Given
        int chargeThreads = 50;
        int useThreads = 30;
        int totalThreads = chargeThreads + useThreads;

        BigDecimal chargeAmount = new BigDecimal("2000");
        BigDecimal useAmount = new BigDecimal("1500");

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(totalThreads);

        AtomicInteger chargeSuccess = new AtomicInteger(0);
        AtomicInteger useSuccess = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        // When: 충전 스레드
        for (int i = 0; i < chargeThreads; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.chargePoint(testUserId, chargeAmount, "혼합 테스트 충전 " + index);
                    chargeSuccess.incrementAndGet();
                    System.out.println("✅ 충전 성공 [" + index + "]");
                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.err.println("⚠️ 충전 실패 [" + index + "]: " + e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // 사용 스레드
        for (int i = 0; i < useThreads; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    pointService.usePoint(testUserId, useAmount, "혼합 테스트 사용 " + index);
                    useSuccess.incrementAndGet();
                    System.out.println("✅ 사용 성공 [" + index + "]");
                } catch (Exception e) {
                    failures.incrementAndGet();
                    System.out.println("❌ 사용 실패 [" + index + "]: 잔액 부족");
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completeLatch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        System.out.println("\n=== Redis 분산락 혼합 작업 테스트 결과 ===");
        System.out.println("충전 성공: " + chargeSuccess.get() + "건");
        System.out.println("사용 성공: " + useSuccess.get() + "건");
        System.out.println("실패: " + failures.get() + "건");

        assertThat(chargeSuccess.get()).isEqualTo(chargeThreads);

        Point finalPoint = pointService.getPointBalance(testUserId);

        // 예상 잔액 계산
        BigDecimal expectedBalance = initialBalance
                .add(chargeAmount.multiply(new BigDecimal(chargeSuccess.get())))
                .subtract(useAmount.multiply(new BigDecimal(useSuccess.get())));

        System.out.println("최종 잔액: " + finalPoint.getBalance());
        System.out.println("예상 잔액: " + expectedBalance);
        System.out.println("충전 총액: " + chargeAmount.multiply(new BigDecimal(chargeSuccess.get())));
        System.out.println("사용 총액: " + useAmount.multiply(new BigDecimal(useSuccess.get())));

        // ✅ 핵심: usingComparator로 BigDecimal 비교
        assertThat(finalPoint.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .as("최종 잔액이 예상과 일치해야 함\n" +
                                "초기: %s\n" +
                                "충전: %d건 × %s = %s\n" +
                                "사용: %d건 × %s = %s\n" +
                                "예상 최종: %s\n" +
                                "실제 최종: %s",
                        initialBalance,
                        chargeSuccess.get(), chargeAmount, chargeAmount.multiply(new BigDecimal(chargeSuccess.get())),
                        useSuccess.get(), useAmount, useAmount.multiply(new BigDecimal(useSuccess.get())),
                        expectedBalance,
                        finalPoint.getBalance())
                .isEqualTo(expectedBalance);

        assertThat(finalPoint.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("분산락 포인트 충전: 순차적으로 정확히 처리")
    void shouldProcessChargesSequentiallyWithLock() throws InterruptedException {
        // Given
        int threadCount = 10;
        BigDecimal chargeAmount = new BigDecimal("500");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(testUserId, chargeAmount, "순차 충전 테스트");
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        completeLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        Point finalPoint = pointService.getPointBalance(testUserId);
        BigDecimal expectedBalance = initialBalance.add(chargeAmount.multiply(new BigDecimal(threadCount)));

        assertThat(finalPoint.getBalance())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(expectedBalance);
    }
}