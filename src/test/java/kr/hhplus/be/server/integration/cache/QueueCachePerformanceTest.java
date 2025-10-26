package kr.hhplus.be.server.integration.cache;

import kr.hhplus.be.server.application.service.QueueTokenQueryService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.domain.model.QueueToken;
import kr.hhplus.be.server.domain.repository.QueueTokenRepository;
import kr.hhplus.be.server.infrastructure.persistence.QueueTokenJpaRepository;
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
 * 대기열 순위 조회 캐싱 성능 비교 테스트
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
public class QueueCachePerformanceTest {

    @Autowired
    private QueueTokenQueryService queueQueryService;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private QueueTokenJpaRepository queueJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    private Long testConcertId;
    private List<String> testTokens;
    private static final int QUEUE_SIZE = 100;

    @BeforeEach
    void setUp() {
        testConcertId = 1L;
        testTokens = new ArrayList<>();

        cacheManager.getCacheNames().forEach(cacheName ->
                cacheManager.getCache(cacheName).clear());

        List<QueueToken> tokens = IntStream.range(0, QUEUE_SIZE)
                .mapToObj(i -> QueueToken.createWaitingToken((long) i, testConcertId))
                .toList();

        tokens.forEach(token -> {
            QueueToken saved = queueTokenRepository.save(token);
            testTokens.add(saved.getTokenValue());
        });
    }

    @AfterEach
    void tearDown() {
        queueJpaRepository.deleteAllInBatch();
    }

    @Test
    @Order(1)
    @DisplayName("성능 비교 1: 캐시 미적용 - 100회 순위 조회 (매번 DB 조회)")
    void performanceWithoutCache() {
        int queryCount = 100;
        List<Long> queryTimes = new ArrayList<>();
        String targetToken = testTokens.get(50);

        queueQueryService.evictAllPositionCaches();

        System.out.println("\n=== 대기열 순위 조회 - 캐시 미적용 테스트 ===");

        for (int i = 0; i < queryCount; i++) {
            queueQueryService.evictPositionCache(testConcertId, targetToken);

            long startTime = System.nanoTime();
            Integer position = queueQueryService.getWaitingPosition(testConcertId, targetToken);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            queryTimes.add(duration);
            assertThat(position).isNotNull();
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
    @DisplayName("성능 비교 2: 캐시 적용 - 100회 순위 조회 (Cache Hit 90% 이상)")
    void performanceWithCache() {
        int queryCount = 100;
        List<Long> queryTimes = new ArrayList<>();
        int cacheHits = 0;
        String targetToken = testTokens.get(50);

        queueQueryService.evictAllPositionCaches();

        System.out.println("\n=== 대기열 순위 조회 - 캐시 적용 테스트 ===");

        for (int i = 0; i < queryCount; i++) {
            long startTime = System.nanoTime();
            Integer position = queueQueryService.getWaitingPosition(testConcertId, targetToken);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            queryTimes.add(duration);
            assertThat(position).isNotNull();

            if (i > 0 && duration < 3) {
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
    @DisplayName("성능 비교 3: 동시 요청 처리 - 100명이 동시에 순위 조회")
    void concurrentRequestsWithCache() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        List<Long> responseTimes = new CopyOnWriteArrayList<>();

        queueQueryService.evictAllPositionCaches();

        System.out.println("\n=== 대기열 순위 조회 - 동시 요청 처리 테스트 ===");
        System.out.println("동시 사용자: " + threadCount + "명");

        for (int i = 0; i < threadCount; i++) {
            int tokenIndex = i % testTokens.size();
            String token = testTokens.get(tokenIndex);

            executorService.submit(() -> {
                try {
                    startLatch.await();

                    long startTime = System.nanoTime();
                    Integer position = queueQueryService.getWaitingPosition(testConcertId, token);
                    long duration = (System.nanoTime() - startTime) / 1_000_000;

                    responseTimes.add(duration);
                    assertThat(position).isNotNull();

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
    @DisplayName("성능 비교 4: 다양한 토큰 조회 - 캐시 분산 효과")
    void multipleTokensCacheDistribution() {
        int queryCount = 200;
        List<Long> queryTimes = new ArrayList<>();
        int cacheHits = 0;

        queueQueryService.evictAllPositionCaches();

        System.out.println("\n=== 대기열 순위 조회 - 다중 토큰 캐시 분산 테스트 ===");

        for (int i = 0; i < queryCount; i++) {
            String token = testTokens.get(i % testTokens.size());

            long startTime = System.nanoTime();
            Integer position = queueQueryService.getWaitingPosition(testConcertId, token);
            long duration = (System.nanoTime() - startTime) / 1_000_000;

            queryTimes.add(duration);
            assertThat(position).isNotNull();

            if (i >= testTokens.size() && duration < 3) {
                cacheHits++;
            }
        }

        long avgTime = (long) queryTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        int expectedHits = queryCount - testTokens.size();
        double cacheHitRatio = (cacheHits * 100.0) / expectedHits;

        System.out.println("\n[결과] 다중 토큰 캐시 분산");
        System.out.println("총 조회 횟수: " + queryCount + "회");
        System.out.println("고유 토큰 수: " + testTokens.size() + "개");
        System.out.println("캐시 히트: " + cacheHits + "회");
        System.out.println("캐시 히트율: " + String.format("%.1f", cacheHitRatio) + "%");
        System.out.println("평균 응답 시간: " + avgTime + " ms");

        assertThat(cacheHitRatio).isGreaterThan(85.0);
    }
}

