package kr.hhplus.be.server.application.service;

import kr.hhplus.be.server.domain.model.SeatReservation;
import kr.hhplus.be.server.domain.repository.SeatReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * 좌석 조회 서비스 (Redis 캐싱 적용)
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SeatReservationQueryService {

    private final SeatReservationRepository seatReservationRepository;

    /**
     * 예약 가능한 좌석 목록 조회 (캐싱)
     *
     * 캐시 전략: Look-Aside (Cache-Aside)
     * TTL: 30초
     * 키: "seatAvailability::concertId"
     *
     * 중요: 좌석 예약 시 캐시 무효화 필요
     */
    @Cacheable(
            value = "seatAvailability",
            key = "#concertId",
            unless = "#result == null || #result.isEmpty()",
            condition = "#concertId != null"
    )
    public List<SeatReservation> getAvailableSeats(Long concertId) {
        log.info("[Cache Miss] DB 조회: getAvailableSeats(concertId={})", concertId);
        return seatReservationRepository.findAvailableSeats(concertId);
    }

    /**
     * 캐시 무효화 (좌석 예약 시 호출)
     *
     * 호출 시점:
     * - 좌석 임시 예약 트랜잭션 커밋 후
     * - 좌석 예약 확정 트랜잭션 커밋 후
     * - 좌석 예약 취소 트랜잭션 커밋 후
     */
    @CacheEvict(value = "seatAvailability", key = "#concertId")
    public void evictSeatCache(Long concertId) {
        log.info("[Cache Evict] 좌석 캐시 삭제: concertId={}", concertId);
    }

    /**
     * 모든 좌석 캐시 무효화 (관리자 기능)
     */
    @CacheEvict(value = "seatAvailability", allEntries = true)
    public void evictAllSeatCaches() {
        log.info("[Cache Evict] 모든 좌석 캐시 삭제");
    }
}