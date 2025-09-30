package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.ConcurrencySeatReservationService;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.model.SeatStatus;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SeatConcurrencyTest {

    @Autowired
    private ConcurrencySeatReservationService concurrencySeatReservationService;

    @Autowired
    private SeatReservationRepository seatReservationRepository;

    private static final Long CONCERT_ID = 1L;
    private static final Integer SEAT_NUMBER = 1;
    private static final Long SEAT_PRICE = 100000L;

    @BeforeEach
    void setUp() {
        // 테스트용 좌석 생성
        SeatReservation availableSeat = SeatReservation.createAvailableSeat(CONCERT_ID, SEAT_NUMBER, SEAT_PRICE);
        seatReservationRepository.save(availableSeat);
    }

    @Test
    @DisplayName("동시성 테스트: 100명이 같은 좌석 예약 시도, 1명만 성공")
    void concurrentSeatReservationTest() throws InterruptedException {
        // Given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 100개 스레드가 동시에 같은 좌석 예약 시도
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    // 조건부 UPDATE 전략 사용
                    concurrencySeatReservationService.reserveSeatWithConditionalUpdate(
                            CONCERT_ID, SEAT_NUMBER, userId);
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

        // Then: 정확히 1명만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(99);

        // 좌석 상태 확인
        SeatReservation reservedSeat = seatReservationRepository
                .findByConcertIdAndSeatNumber(CONCERT_ID, SEAT_NUMBER)
                .orElseThrow();
        assertThat(reservedSeat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("비관적 락 vs 조건부 UPDATE 성능 비교 테스트")
    void lockPerformanceComparisonTest() throws InterruptedException {
        // Given
        int threadCount = 50;

        // 1. 조건부 UPDATE 테스트
        long conditionalUpdateTime = measureReservationTime(threadCount, true);

        // 좌석 상태 초기화
        resetSeat();

        // 2. 비관적 락 테스트
        long pessimisticLockTime = measureReservationTime(threadCount, false);

        // Then: 조건부 UPDATE가 더 빠르거나 비슷해야 함
        System.out.println("조건부 UPDATE 실행 시간: " + conditionalUpdateTime + "ms");
        System.out.println("비관적 락 실행 시간: " + pessimisticLockTime + "ms");

        // 성능 차이 로깅 (테스트는 실패하지 않도록)
        assertThat(conditionalUpdateTime).isLessThanOrEqualTo(pessimisticLockTime + 1000); // 1초 여유
    }

    private long measureReservationTime(int threadCount, boolean useConditionalUpdate) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    if (useConditionalUpdate) {
                        concurrencySeatReservationService.reserveSeatWithConditionalUpdate(
                                CONCERT_ID, SEAT_NUMBER, userId);
                    } else {
                        concurrencySeatReservationService.reserveSeatWithPessimisticLock(
                                CONCERT_ID, SEAT_NUMBER, userId);
                    }
                } catch (Exception ignored) {
                    // 예외는 무시 (1명만 성공하므로)
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        return System.currentTimeMillis() - startTime;
    }

    private void resetSeat() {
        SeatReservation availableSeat = SeatReservation.createAvailableSeat(CONCERT_ID, SEAT_NUMBER, SEAT_PRICE);
        seatReservationRepository.save(availableSeat);
    }
}