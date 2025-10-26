package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.repository.QueueTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기열 조회 서비스 (Redis 캐싱 적용)
 *
 * 캐시 전략: Look-Aside
 * TTL: 10초 (준실시간)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QueueTokenQueryService {

    private final QueueTokenRepository queueTokenRepository;

    /**
     * 대기열 순위 조회 (캐싱)
     *
     * 키: "queuePosition::concertId:tokenValue"
     * TTL: 10초
     *
     * 주의: 토큰 활성화 시 캐시 무효화 필요
     */
    @Cacheable(
            value = "queuePosition",
            key = "#concertId + ':' + #tokenValue",
            unless = "#result == null",
            condition = "#concertId != null && #tokenValue != null"
    )
    public Integer getWaitingPosition(Long concertId, String tokenValue) {
        log.info("🔍 [Cache Miss] DB 조회: getWaitingPosition(concertId={}, token={})",
                concertId, tokenValue);
        return queueTokenRepository.getWaitingPosition(concertId, tokenValue);
    }

    /**
     * 캐시 무효화 (토큰 활성화 시 호출)
     */
    @CacheEvict(value = "queuePosition", key = "#concertId + ':' + #tokenValue")
    public void evictPositionCache(Long concertId, String tokenValue) {
        log.info("🗑️ [Cache Evict] 대기열 캐시 삭제: concertId={}, token={}",
                concertId, tokenValue);
    }

    /**
     * 특정 콘서트의 모든 대기열 캐시 무효화
     */
    @CacheEvict(value = "queuePosition", allEntries = true)
    public void evictAllPositionCaches() {
        log.info("🗑️ [Cache Evict] 모든 대기열 캐시 삭제");
    }
}