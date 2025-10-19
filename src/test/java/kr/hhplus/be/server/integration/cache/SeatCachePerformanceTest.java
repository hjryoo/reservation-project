package kr.hhplus.be.server.integration.cache;

import kr.hhplus.be.server.application.service.SeatReservationQueryService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import kr.hhplus.be.server.infrastructure.persistence.SeatReservationJpaRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * 좌석 현황 조회 캐싱 성능 비교 테스트
 *
 * 비교 대상:
 * 1. 캐시 미적용 (매번 DB 조회)
 * 2. 캐시 적용 (Redis 캐시 사용)
 * 3. 동시 요청 처리 능력
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(RedisTestContainerConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeatCachePerformanceTest {

    @Autowired
    private SeatReservationQueryService seatQueryService;

    @Autowired
    private SeatReservationRepository seatReservationRepository;

    @Autowired
    private SeatReservationJpaRepository seatJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    private Long testConcertId;
    private static final int SEAT_COUNT = 100;

    @BeforeEach
    void setUp() {
        testConcertId = 1L;

        cacheManager.getCacheNames().forEach(cacheName ->
                cacheManager.getCache(cacheName).clear());

        List<SeatReservation> seats = IntStream.rangeClosed(1, SEAT_COUNT)
                .mapToObj(i -> SeatReservation.createAvailableSeat(testConcertId, i, 50000L))
                .toList();

        seats.forEach(seatReservationRepository::save);
    }

    @AfterEach
    void tearDown() {
        seatJpaRepository.deleteAllInBatch();
    }

    @Test
    @Order(1)
    @DisplayName("성능 비교 1: 캐시 미적용 - 100회 연속 조회 (매번 DB 조회)")
    void performanceWithoutCache() {
        int queryCount = 100;
        List<Long> queryTimes = new ArrayList<>();

        seatQueryService.evictAllSeatCaches();

        System.out.println("\n=== 캐시 미적용 성능 테스트 시작 ===");

        for (int i = 0; i < queryCount; i++) {
            seatQueryService.evictSeatCache(testConcertId);

            long startTime = System.nanoTime();
            List<SeatReservation> seats = seatQueryService.getAvailableSeats(testConcertId);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            queryTimes.add(duration);
            assertThat(seats).hasSize(SEAT_COUNT);
        }

        long avgTime = (long) queryTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long minTime = queryTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxTime = queryTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long totalTime = queryTimes.stream().mapToLong(Long::longValue).sum();

        System.out.println("\n[결과] 캐시 미적용");
        System.out.println("총 조회 횟수: " + queryCount + "회");
        System.out.println("평균 응답 시간: " + avgTime + " ms");
        System.out.println("최소 응답 시간: " + minTime + " ms");
        System.out.println("최대 응답 시간: " + maxTime + " ms");
        System.out.println("전체 소요 시간: " + totalTime + " ms");
        System.out.println("TPS: " + String.format("%.2f", queryCount * 1000.0 / totalTime) + " req/sec");
    }

    @Test
    @Order(2)
    @DisplayName("성능 비교 2: 캐시 적용 - 100회 연속 조회 (Cache Hit 90% 이상)")
    void performanceWithCache() {
        int queryCount = 100;
        List<Long> queryTimes = new ArrayList<>();
        int cacheHits = 0;

        seatQueryService.evictAllSeatCaches();

        System.out.println("\n=== 캐시 적용 성능 테스트 시작 ===");

        for (int i = 0; i < queryCount; i++) {
            long startTime = System.nanoTime();
            List<SeatReservation> seats = seatQueryService.getAvailableSeats(testConcertId);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            queryTimes.add(duration);
            assertThat(seats).hasSize(SEAT_COUNT);

            if (i > 0 && duration < 5) {
                cacheHits++;
            }
        }

        long avgTime = (long) queryTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long minTime = queryTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxTime = queryTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        long totalTime = queryTimes.stream().mapToLong(Long::longValue).sum();
        double cacheHitRatio = (cacheHits * 100.0) / (queryCount - 1);

        System.out.println("\n[결과] 캐시 적용");
        System.out.println("총 조회 횟수: " + queryCount + "회");
        System.out.println("캐시 히트: " + cacheHits + "회");
        System.out.println("캐시 히트율: " + String.format("%.1f", cacheHitRatio) + "%");
        System.out.println("평균 응답 시간: " + avgTime + " ms");
        System.out.println("최소 응답 시간: " + minTime + " ms");
        System.out.println("최대 응답 시간: " + maxTime + " ms");
        System.out.println("전체 소요 시간: " + totalTime + " ms");
        System.out.println("TPS: " + String.format("%.2f", queryCount * 1000.0 / totalTime) + " req/sec");

        assertThat(cacheHitRatio).isGreaterThan(90.0);
    }

    @Test
    @Order(3)
    @DisplayName("성능 비교 3: 동시 요청 처리 - 100명이 동시에 좌석 조회")
    void concurrentRequestsWithCache() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        List<Long> responseTimes = new CopyOnWriteArrayList<>();

        seatQueryService.evictAllSeatCaches();

        System.out.println("\n=== 동시 요청 처리 테스트 시작 ===");
        System.out.println("동시 사용자: " + threadCount + "명");

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    long startTime = System.nanoTime();
                    List<SeatReservation> seats = seatQueryService.getAvailableSeats(testConcertId);
                    long duration = (System.nanoTime() - startTime) / 1_000_000;

                    responseTimes.add(duration);
                    assertThat(seats).hasSize(SEAT_COUNT);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        long testStartTime = System.currentTimeMillis();
        startLatch.countDown();
        completeLatch.await(60, TimeUnit.SECONDS);
        long testDuration = System.currentTimeMillis() - testStartTime;
        executorService.shutdown();

        long avgTime = (long) responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long minTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.println("\n[결과] 동시 요청 처리");
        System.out.println("총 요청 수: " + threadCount + "건");
        System.out.println("평균 응답 시간: " + avgTime + " ms");
        System.out.println("최소 응답 시간: " + minTime + " ms");
        System.out.println("최대 응답 시간: " + maxTime + " ms");
        System.out.println("전체 소요 시간: " + testDuration + " ms");
        System.out.println("처리량: " + String.format("%.2f", threadCount * 1000.0 / testDuration) + " req/sec");

        assertThat(responseTimes).hasSize(threadCount);
    }

    @Test
    @Order(4)
    @DisplayName("성능 비교 4: 캐시 무효화 영향 테스트")
    void cacheEvictionImpact() {
        int warmupCount = 10;
        int testCount = 50;
        List<Long> beforeEviction = new ArrayList<>();
        List<Long> afterEviction = new ArrayList<>();

        seatQueryService.evictAllSeatCaches();

        System.out.println("\n=== 캐시 무효화 영향 테스트 시작 ===");

        for (int i = 0; i < warmupCount; i++) {
            seatQueryService.getAvailableSeats(testConcertId);
        }

        for (int i = 0; i < testCount; i++) {
            long startTime = System.nanoTime();
            List<SeatReservation> seats = seatQueryService.getAvailableSeats(testConcertId);
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            beforeEviction.add(duration);
            assertThat(seats).hasSize(SEAT_COUNT);
        }

        seatQueryService.evictSeatCache(testConcertId);

        for (int i = 0; i < testCount; i++) {
            long startTime = System.nanoTime();
            List<SeatReservation> seats = seatQueryService.getAvailableSeats(testConcertId);
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            afterEviction.add(duration);
            assertThat(seats).hasSize(SEAT_COUNT);
        }

        long avgBefore = (long) beforeEviction.stream().mapToLong(Long::longValue).average().orElse(0);
        long avgAfter = (long) afterEviction.stream().mapToLong(Long::longValue).average().orElse(0);

        System.out.println("\n[결과] 캐시 무효화 영향");
        System.out.println("캐시 무효화 전 평균: " + avgBefore + " ms");
        System.out.println("캐시 무효화 후 평균: " + avgAfter + " ms");
        System.out.println("성능 차이: " + (avgAfter - avgBefore) + " ms");
    }
}