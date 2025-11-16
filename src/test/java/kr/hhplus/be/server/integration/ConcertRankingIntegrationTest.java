package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.application.service.ConcertRankingService;
import kr.hhplus.be.server.config.RedisTestContainerConfig;
import kr.hhplus.be.server.config.TestEventConfig;
import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import kr.hhplus.be.server.infrastructure.persistence.SpringDataConcertRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 콘서트 매진 랭킹 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({RedisTestContainerConfig.class, TestEventConfig.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConcertRankingIntegrationTest {

    @Autowired
    private ConcertRankingService rankingService;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SpringDataConcertRepository jpaRepository;

    @Autowired
    @Qualifier("rankingRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
        redisTemplate.execute((RedisConnection connection) -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        jpaRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("매진 콘서트 랭킹 등록 - 단일 콘서트")
    void registerSoldOutConcert_Single() throws InterruptedException {
        Concert concert = createConcert("아이유 콘서트", "아이유", "올림픽공경기장", 50000, 50000L);
        concert.openBooking();

        Thread.sleep(1000);

        concert.markAsSoldOut();
        Concert saved = concertRepository.save(concert);

        rankingService.registerSoldOutConcert(saved);

        List<ConcertRankingService.ConcertRankingDto> dailyRanking =
                rankingService.getTopDailySoldOutConcerts(10);

        assertThat(dailyRanking).hasSize(1);
        assertThat(dailyRanking.get(0).rank()).isEqualTo(1);
        assertThat(dailyRanking.get(0).concertId()).isEqualTo(saved.getId());
        assertThat(dailyRanking.get(0).title()).isEqualTo("아이유 콘서트");
        assertThat(dailyRanking.get(0).durationSeconds()).isGreaterThan(0L);
    }

    @Test
    @Order(2)
    @DisplayName("매진 콘서트 랭킹 - 여러 콘서트 빠른 순서대로 정렬")
    void registerMultipleConcerts_SortedBySpeed() throws InterruptedException {
        // Given: 100ms, 300ms, 500ms 차이로 매진
        Concert concert1 = createAndSellOut("BTS 콘서트", "BTS", "잠실주경기장", 50000, 100L);
        Concert concert2 = createAndSellOut("블랙핑크 콘서트", "블랙핑크", "고척돔", 30000, 500L);
        Concert concert3 = createAndSellOut("세븐틴 콘서트", "세븐틴", "올림픽공경기장", 40000, 300L);

        // When
        rankingService.registerSoldOutConcert(concert1);
        rankingService.registerSoldOutConcert(concert2);
        rankingService.registerSoldOutConcert(concert3);

        // Then
        List<ConcertRankingService.ConcertRankingDto> ranking =
                rankingService.getTopDailySoldOutConcerts(10);

        assertThat(ranking).hasSize(3);

        // 1등: BTS (100ms = 0초)
        assertThat(ranking.get(0).rank()).isEqualTo(1);
        assertThat(ranking.get(0).title()).isEqualTo("BTS 콘서트");
        assertThat(ranking.get(0).durationSeconds()).isEqualTo(0L);

        // 2등: 세븐틴 (300ms = 0초)
        assertThat(ranking.get(1).rank()).isEqualTo(2);
        assertThat(ranking.get(1).title()).isEqualTo("세븐틴 콘서트");
        assertThat(ranking.get(1).durationSeconds()).isEqualTo(0L);

        // 3등: 블랙핑크 (500ms = 0초)
        assertThat(ranking.get(2).rank()).isEqualTo(3);
        assertThat(ranking.get(2).title()).isEqualTo("블랙핑크 콘서트");
        assertThat(ranking.get(2).durationSeconds()).isEqualTo(0L);
    }

    @Test
    @Order(3)
    @DisplayName("일간/주간/월간 랭킹 모두 정상 등록")
    void registerAllPeriodRankings() throws InterruptedException {
        Concert concert = createAndSellOut("아이유 콘서트", "아이유", "올림픽공경기장", 50000, 200L);

        rankingService.registerSoldOutConcert(concert);

        assertThat(rankingService.getTopDailySoldOutConcerts(10)).hasSize(1);
        assertThat(rankingService.getTopWeeklySoldOutConcerts(10)).hasSize(1);
        assertThat(rankingService.getTopMonthlySoldOutConcerts(10)).hasSize(1);
        assertThat(rankingService.getTopAllTimeSoldOutConcerts(10)).hasSize(1);
    }

    @Test
    @Order(4)
    @DisplayName("특정 콘서트의 순위 조회")
    void getConcertRank() throws InterruptedException {
        Concert concert1 = createAndSellOut("콘서트1", "아티스트1", "장소1", 10000, 100L);
        Concert concert2 = createAndSellOut("콘서트2", "아티스트2", "장소2", 20000, 200L);
        Concert concert3 = createAndSellOut("콘서트3", "아티스트3", "장소3", 30000, 300L);

        rankingService.registerSoldOutConcert(concert1);
        rankingService.registerSoldOutConcert(concert2);
        rankingService.registerSoldOutConcert(concert3);

        Long rank1 = rankingService.getConcertRank(concert1.getId(),
                ConcertRankingService.RankingPeriod.DAILY);
        Long rank2 = rankingService.getConcertRank(concert2.getId(),
                ConcertRankingService.RankingPeriod.DAILY);
        Long rank3 = rankingService.getConcertRank(concert3.getId(),
                ConcertRankingService.RankingPeriod.DAILY);

        assertThat(rank1).isEqualTo(1L);
        assertThat(rank2).isEqualTo(2L);
        assertThat(rank3).isEqualTo(3L);
    }

    @Test
    @Order(5)
    @DisplayName("Top N 제한 - 10개만 조회")
    void getTopN_Limit() throws InterruptedException {
        for (int i = 1; i <= 15; i++) {
            Concert concert = createAndSellOut("콘서트" + i, "아티스트" + i, "장소" + i,
                    10000, i * 100L);
            rankingService.registerSoldOutConcert(concert);
        }

        List<ConcertRankingService.ConcertRankingDto> top10 =
                rankingService.getTopDailySoldOutConcerts(10);

        assertThat(top10).hasSize(10);
        assertThat(top10.get(0).rank()).isEqualTo(1);
        assertThat(top10.get(9).rank()).isEqualTo(10);
    }

    @Test
    @Order(6)
    @DisplayName("매진 시간 포맷팅 검증")
    void formattedDuration() throws InterruptedException {
        // Given: 실제 시간 경과로 측정 가능한 매진 시간
        Concert concert1 = createAndSellOut("30초 매진", "아티스트", "장소", 10000, 1000L); // 1초
        Concert concert2 = createAndSellOut("5분 매진", "아티스트", "장소", 10000, 2000L); // 2초
        Concert concert3 = createAndSellOut("1시간 매진", "아티스트", "장소", 10000, 3000L); // 3초

        rankingService.registerSoldOutConcert(concert1);
        rankingService.registerSoldOutConcert(concert2);
        rankingService.registerSoldOutConcert(concert3);

        // When
        List<ConcertRankingService.ConcertRankingDto> ranking =
                rankingService.getTopDailySoldOutConcerts(10);

        // Then: 실제 경과 시간 기준으로 검증
        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).durationSeconds()).isEqualTo(1L);
        assertThat(ranking.get(0).formattedDuration()).isEqualTo("1초");

        assertThat(ranking.get(1).durationSeconds()).isEqualTo(2L);
        assertThat(ranking.get(1).formattedDuration()).isEqualTo("2초");

        assertThat(ranking.get(2).durationSeconds()).isEqualTo(3L);
        assertThat(ranking.get(2).formattedDuration()).isEqualTo("3초");
    }

    @Test
    @Order(7)
    @DisplayName("매진되지 않은 콘서트 등록 시 예외 발생")
    void registerNonSoldOutConcert_ThrowsException() {
        Concert concert = createConcert("예약 중 콘서트", "아티스트", "장소", 50000, 50000L);
        Concert saved = concertRepository.save(concert);

        assertThatThrownBy(() -> rankingService.registerSoldOutConcert(saved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("매진되지 않은 콘서트");
    }

    private Concert createConcert(String title, String artist, String venue,
                                  Integer totalSeats, Long price) {
        return Concert.create(title, artist, venue, totalSeats, price);
    }

    /**
     * 매진 콘서트 생성 (실제 시간 경과)
     *
     * @param delayMs 매진까지 소요 시간 (밀리초)
     */
    private Concert createAndSellOut(String title, String artist, String venue,
                                     Integer totalSeats, Long delayMs) throws InterruptedException {
        Concert concert = createConcert(title, artist, venue, totalSeats, 50000L);
        concert.openBooking();

        // 실제 시간 경과 (Thread.sleep 사용)
        Thread.sleep(delayMs);

        concert.markAsSoldOut();
        return concertRepository.save(concert);
    }
}
