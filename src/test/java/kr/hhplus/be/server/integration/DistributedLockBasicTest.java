package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.config.TestEventConfig;
import kr.hhplus.be.server.integration.helper.TestLockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Redis 분산락 기본 동작 검증 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({RedisTestContainerConfig.class, TestEventConfig.class})
public class DistributedLockBasicTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private TestLockService testLockService;

    @Test
    @DisplayName("분산락 기본 동작: 락 획득 → 작업 수행 → 락 해제")
    void shouldAcquireAndReleaseLockSuccessfully() throws Exception {
        // Given
        String testKey = "test-key-1";

        // When
        String result = testLockService.executeWithLock(testKey);

        // Then
        assertThat(result).isEqualTo("success");

        // 락이 해제되었는지 확인
        assertThat(redissonClient.getLock("test:lock:" + testKey).isLocked()).isFalse();
    }

    @Test
    @DisplayName("분산락 동시성 제어: 10개 스레드가 동시 실행 시 순차 처리")
    void shouldProcessSequentiallyWithDistributedLock() throws InterruptedException {
        // Given
        String sharedKey = "shared-resource";
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 counter 증가 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    testLockService.incrementCounter(sharedKey, counter);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = completeLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 모든 스레드가 성공하고 counter는 정확히 10
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(counter.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("분산락 타임아웃 테스트: 락 대기 시간 초과 시 예외 발생")
    void shouldThrowExceptionWhenLockAcquisitionTimeout() throws Exception {
        // Given
        String testKey = "timeout-test";

        // 먼저 락을 획득하고 오래 점유
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);

        Future<String> firstTask = executorService.submit(() -> {
            return testLockService.holdLockForLongTime(testKey, firstLockAcquired);
        });

        // 첫 번째 락이 획득될 때까지 대기
        boolean acquired = firstLockAcquired.await(5, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();

        // When: 두 번째 스레드가 락 획득 시도 (타임아웃 예상)
        Future<String> secondTask = executorService.submit(() -> {
            return testLockService.executeWithLock(testKey);
        });

        // Then: 두 번째 요청은 타임아웃으로 실패해야 함
        assertThatThrownBy(() -> secondTask.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("락 획득 실패");

        executorService.shutdownNow();
    }

    @Test
    @DisplayName("분산락 예외 발생 시 락 해제 보장")
    void shouldReleaseLockEvenWhenExceptionOccurs() {
        // Given
        String testKey = "exception-test";

        // When: 비즈니스 로직에서 예외 발생
        assertThatThrownBy(() -> testLockService.executeWithException(testKey))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Business logic error");

        // Then: 락이 해제되어야 함
        assertThat(redissonClient.getLock("test:lock:" + testKey).isLocked()).isFalse();
    }
}
