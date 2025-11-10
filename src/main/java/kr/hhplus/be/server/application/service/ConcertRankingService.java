package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.Concert;
import kr.hhplus.be.server.domain.repository.ConcertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 콘서트 빠른 매진 랭킹 서비스 (Redis Sorted Set 활용)
 */
@Slf4j
@Service
public class ConcertRankingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ConcertRepository concertRepository;

    private static final String RANKING_KEY_PREFIX = "concert:soldout:ranking:";
    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTHLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    public ConcertRankingService(
            @Qualifier("rankingRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ConcertRepository concertRepository) {
        this.redisTemplate = redisTemplate;
        this.concertRepository = concertRepository;
    }

    /**
     * 매진 콘서트 랭킹 등록
     *
     * 매진된 시점을 기준으로 일간/주간/월간/전체 랭킹에 등록
     */
    public void registerSoldOutConcert(Concert concert) {
        if (!concert.isSoldOut()) {
            throw new IllegalArgumentException("매진되지 않은 콘서트는 랭킹에 등록할 수 없습니다.");
        }

        Long durationMillis = concert.calculateSoldOutDurationMillis();
        String concertId = concert.getId().toString();

        LocalDate soldOutDate = concert.getSoldOutAt().toLocalDate();

        log.info("매진 랭킹 등록 - concertId: {}, title: {}, duration: {}ms ({}초)",
                concertId, concert.getTitle(), durationMillis, durationMillis / 1000.0);

        registerDailyRanking(concertId, durationMillis, soldOutDate);
        registerWeeklyRanking(concertId, durationMillis, soldOutDate);
        registerMonthlyRanking(concertId, durationMillis, soldOutDate);
        registerAllTimeRanking(concertId, durationMillis);
    }

    /**
     * 일간 랭킹 등록
     *
     * 매진된 날짜를 기준으로 집계
     */
    private void registerDailyRanking(String concertId, Long durationMillis, LocalDate soldOutDate) {
        String key = getDailyRankingKey(soldOutDate);
        redisTemplate.opsForZSet().add(key, concertId, durationMillis);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
        log.debug("일간 랭킹 등록 - key: {}, concertId: {}, score: {}ms", key, concertId, durationMillis);
    }

    /**
     * 주간 랭킹 등록
     */
    private void registerWeeklyRanking(String concertId, Long durationMillis, LocalDate soldOutDate) {
        String key = getWeeklyRankingKey(soldOutDate);
        redisTemplate.opsForZSet().add(key, concertId, durationMillis);
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
        log.debug("주간 랭킹 등록 - key: {}, concertId: {}, score: {}ms", key, concertId, durationMillis);
    }

    /**
     * 월간 랭킹 등록
     */
    private void registerMonthlyRanking(String concertId, Long durationMillis, LocalDate soldOutDate) {
        String key = getMonthlyRankingKey(soldOutDate);
        redisTemplate.opsForZSet().add(key, concertId, durationMillis);
        redisTemplate.expire(key, 365, TimeUnit.DAYS);
        log.debug("월간 랭킹 등록 - key: {}, concertId: {}, score: {}ms", key, concertId, durationMillis);
    }

    /**
     * 전체 랭킹 등록
     */
    private void registerAllTimeRanking(String concertId, Long durationMillis) {
        String key = RANKING_KEY_PREFIX + "all";
        redisTemplate.opsForZSet().add(key, concertId, durationMillis);
        log.debug("전체 랭킹 등록 - key: {}, concertId: {}, score: {}ms", key, concertId, durationMillis);
    }

    /**
     * Top N 조회
     */
    public List<ConcertRankingDto> getTopDailySoldOutConcerts(int limit) {
        String key = getDailyRankingKey(LocalDate.now());
        return getTopRankingWithDetails(key, limit);
    }

    public List<ConcertRankingDto> getTopWeeklySoldOutConcerts(int limit) {
        String key = getWeeklyRankingKey(LocalDate.now());
        return getTopRankingWithDetails(key, limit);
    }

    public List<ConcertRankingDto> getTopMonthlySoldOutConcerts(int limit) {
        String key = getMonthlyRankingKey(LocalDate.now());
        return getTopRankingWithDetails(key, limit);
    }

    public List<ConcertRankingDto> getTopAllTimeSoldOutConcerts(int limit) {
        String key = RANKING_KEY_PREFIX + "all";
        return getTopRankingWithDetails(key, limit);
    }

    /**
     * 특정 콘서트의 순위 조회
     */
    public Long getConcertRank(Long concertId, RankingPeriod period) {
        String key = getRankingKey(period, LocalDate.now());
        Long rank = redisTemplate.opsForZSet().rank(key, concertId.toString());
        return rank != null ? rank + 1 : null;
    }

    /**
     * Score 순으로 랭킹 조회 및 Concert 정보 조인
     */
    private List<ConcertRankingDto> getTopRankingWithDetails(String key, int limit) {
        Set<ZSetOperations.TypedTuple<String>> topEntries =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, limit - 1);

        if (topEntries == null || topEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConcertRankingDto> results = new ArrayList<>();
        int rank = 1;

        for (ZSetOperations.TypedTuple<String> entry : topEntries) {
            Long concertId = Long.parseLong(entry.getValue());
            Long durationMillis = entry.getScore().longValue();
            Long durationSeconds = durationMillis / 1000;

            Optional<Concert> concertOpt = concertRepository.findById(concertId);
            if (concertOpt.isPresent()) {
                Concert concert = concertOpt.get();
                results.add(new ConcertRankingDto(
                        rank++,
                        concert.getId(),
                        concert.getTitle(),
                        concert.getArtist(),
                        concert.getVenue(),
                        durationSeconds,
                        formatDuration(durationSeconds)
                ));
            }
        }

        return results;
    }

    /**
     * Duration 포맷팅
     */
    private String formatDuration(Long durationSeconds) {
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;

        if (hours > 0) {
            return String.format("%d시간 %d분 %d초", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d분 %d초", minutes, seconds);
        } else {
            return String.format("%d초", seconds);
        }
    }

    /**
     * Redis Key 생성
     */
    private String getDailyRankingKey(LocalDate date) {
        return RANKING_KEY_PREFIX + "daily:" + date.format(DAILY_FORMAT);
    }

    private String getWeeklyRankingKey(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        int year = date.getYear();
        int week = date.get(weekFields.weekOfWeekBasedYear());
        return String.format("%sweekly:%d-W%02d", RANKING_KEY_PREFIX, year, week);
    }

    private String getMonthlyRankingKey(LocalDate date) {
        return RANKING_KEY_PREFIX + "monthly:" + date.format(MONTHLY_FORMAT);
    }

    private String getRankingKey(RankingPeriod period, LocalDate date) {
        return switch (period) {
            case DAILY -> getDailyRankingKey(date);
            case WEEKLY -> getWeeklyRankingKey(date);
            case MONTHLY -> getMonthlyRankingKey(date);
            case ALL_TIME -> RANKING_KEY_PREFIX + "all";
        };
    }

    /**
     * DTO
     */
    public record ConcertRankingDto(
            int rank,
            Long concertId,
            String title,
            String artist,
            String venue,
            Long durationSeconds,
            String formattedDuration
    ) {}

    /**
     * Enum
     */
    public enum RankingPeriod {
        DAILY, WEEKLY, MONTHLY, ALL_TIME
    }
}