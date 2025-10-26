package kr.hhplus.be.server.integration.helper;

import kr.hhplus.be.server.infrastructure.lock.DistributedLock;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 분산락 테스트를 위한 헬퍼 서비스
 */
@Service
public class TestLockService {

    @DistributedLock(
            key = "'test:lock:' + #key",
            waitTime = 5L,
            leaseTime = 3L
    )
    public String executeWithLock(String key) {
        // 간단한 작업 시뮬레이션
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "success";
    }

    @DistributedLock(
            key = "'test:lock:' + #key",
            waitTime = 5L,
            leaseTime = 3L
    )
    public void incrementCounter(String key, AtomicInteger counter) {
        // 동시성 문제가 발생할 수 있는 작업
        int current = counter.get();
        try {
            Thread.sleep(50); // Race condition 유발
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        counter.set(current + 1);
    }

    @DistributedLock(
            key = "'test:lock:' + #key",
            waitTime = 2L, // 짧은 대기 시간
            leaseTime = 10L
    )
    public String holdLockForLongTime(String key, CountDownLatch latch) {
        latch.countDown(); // 락 획득 알림
        try {
            Thread.sleep(8000); // 8초 동안 락 점유
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "held";
    }

    @DistributedLock(
            key = "'test:lock:' + #key",
            waitTime = 5L,
            leaseTime = 3L
    )
    public void executeWithException(String key) {
        throw new RuntimeException("Business logic error");
    }
}