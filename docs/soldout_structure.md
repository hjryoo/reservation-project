# 콘서트 빠른 매진 랭킹 시스템 설계 문서

## 1. 개요

### 1.1 목적
콘서트 예약 서비스에서 매진 속도를 기준으로 인기도를 측정하고, 실시간 랭킹을 제공하는 시스템

### 1.2 핵심 기능
- 매진 소요 시간 자동 계산 및 랭킹 등록
- 일간/주간/월간/전체 랭킹 제공
- Redis Sorted Set 기반 고성능 조회
- 자동 TTL 관리

***

## 2. 시스템 아키텍처

### 2.1 기술 스택
- Redis Sorted Set: 실시간 랭킹 저장 및 조회
- MySQL: 콘서트 정보 및 매진 시간 영속화
- Spring Boot: 비즈니스 로직 처리
- Spring Transaction: 데이터 정합성 보장

### 2.2 데이터 흐름

```
1. 콘서트 예약 시작 (bookingOpenAt 기록)
   ↓
2. 마지막 좌석 확정 → 자동 매진 처리 (soldOutAt 기록)
   ↓
3. 트랜잭션 커밋 후 매진 소요 시간 계산
   ↓
4. Redis Sorted Set에 랭킹 등록 (밀리초 단위)
   ↓
5. 사용자 조회 시 Redis에서 Top N 반환 (초 단위 변환)
```

***

## 3. Redis 자료구조 설계

### 3.1 Sorted Set 구조

**선택 이유**
- 자동 정렬: Score 기준 오름차순 정렬
- O(log N) 성능: 삽입/조회/순위 계산
- 범위 조회: Top N 효율적 조회

**Key 전략**

| 랭킹 유형 | Key 패턴 | 예시 | TTL |
|---------|---------|------|-----|
| 일간 | concert:soldout:ranking:daily:{YYYYMMDD} | concert:soldout:ranking:daily:20251031 | 7일 |
| 주간 | concert:soldout:ranking:weekly:{YYYY-Www} | concert:soldout:ranking:weekly:2025-W44 | 30일 |
| 월간 | concert:soldout:ranking:monthly:{YYYYMM} | concert:soldout:ranking:monthly:202510 | 365일 |
| 전체 | concert:soldout:ranking:all | concert:soldout:ranking:all | 없음 |

**Score 설계**

```
Score = 매진 소요 시간 (밀리초 단위)
Member = concertId (문자열)

계산식:
durationMillis = soldOutAt - bookingOpenAt (ChronoUnit.MILLIS)

예시:
- 100ms 매진: Score = 100
- 300ms 매진: Score = 300
- 1초 매진: Score = 1000
```

**밀리초 단위 사용 이유**
- 정밀도: 1ms 단위로 정확한 순위 계산
- 동일 Score 방지: 100ms, 300ms 차이 구분 가능
- 조회 시 변환: 사용자에게는 초 단위로 표시

***

## 4. 도메인 모델 설계

### 4.1 Concert 엔티티 확장

```java
public class Concert {
    private Long id;
    private final String title;
    private final String artist;
    private final String venue;
    private final Integer totalSeats;
    private Integer availableSeats;
    private final Long price;
    private ConcertStatus status;
    
    // 매진 추적 필드 (신규)
    private LocalDateTime bookingOpenAt;  // 예약 시작 시간
    private LocalDateTime soldOutAt;      // 매진 완료 시간
    
    // 예약 시작
    public void openBooking() {
        this.bookingOpenAt = LocalDateTime.now();
        this.status = ConcertStatus.AVAILABLE;
    }
    
    // 매진 처리
    public void markAsSoldOut() {
        this.status = ConcertStatus.SOLD_OUT;
        this.soldOutAt = LocalDateTime.now();
    }
    
    // 매진 소요 시간 계산 (밀리초)
    public Long calculateSoldOutDurationMillis() {
        return ChronoUnit.MILLIS.between(bookingOpenAt, soldOutAt);
    }
}
```

### 4.2 데이터베이스 스키마

```sql
ALTER TABLE concerts
ADD COLUMN booking_open_at TIMESTAMP NULL COMMENT '예약 시작 시간',
ADD COLUMN sold_out_at TIMESTAMP NULL COMMENT '매진 완료 시간';

CREATE INDEX idx_concert_soldout ON concerts(sold_out_at);
```

***

## 5. 핵심 비즈니스 로직

### 5.1 랭킹 등록 프로세스

```java
public void registerSoldOutConcert(Concert concert) {
    // 1. 검증
    if (!concert.isSoldOut()) {
        throw new IllegalArgumentException("매진되지 않은 콘서트");
    }
    
    // 2. 매진 소요 시간 계산 (밀리초)
    Long durationMillis = concert.calculateSoldOutDurationMillis();
    String concertId = concert.getId().toString();
    
    // 3. Redis에 등록 (4개 랭킹 동시 저장)
    registerDailyRanking(concertId, durationMillis);    // 일간
    registerWeeklyRanking(concertId, durationMillis);   // 주간
    registerMonthlyRanking(concertId, durationMillis);  // 월간
    registerAllTimeRanking(concertId, durationMillis);  // 전체
}

private void registerDailyRanking(String concertId, Long durationMillis) {
    String key = "concert:soldout:ranking:daily:" + LocalDate.now();
    redisTemplate.opsForZSet().add(key, concertId, durationMillis);
    redisTemplate.expire(key, 7, TimeUnit.DAYS);
}
```

### 5.2 랭킹 조회 프로세스

```java
public List<ConcertRankingDto> getTopDailySoldOutConcerts(int limit) {
    String key = "concert:soldout:ranking:daily:20251031";
    
    // 1. Redis에서 Top N 조회 (Score 포함)
    Set<TypedTuple<String>> topEntries = 
        redisTemplate.opsForZSet().rangeWithScores(key, 0, limit - 1);
    
    // 2. 콘서트 정보 조회 및 DTO 변환
    List<ConcertRankingDto> results = new ArrayList<>();
    int rank = 1;
    
    for (TypedTuple<String> entry : topEntries) {
        Long concertId = Long.parseLong(entry.getValue());
        Long durationMillis = entry.getScore().longValue();
        Long durationSeconds = durationMillis / 1000; // 초로 변환
        
        Concert concert = concertRepository.findById(concertId).get();
        
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
    
    return results;
}
```

***

## 6. 통합 지점 설계

### 6.1 좌석 예약 서비스 연동

```java
@Transactional
public SeatReservation confirmReservation(Long concertId, Integer seatNumber, Long userId) {
    // 1. 좌석 확정
    SeatReservation seat = seatReservationRepository.findByConcertIdAndSeatNumber(...);
    seat.confirm();
    seatReservationRepository.save(seat);
    
    // 2. Concert 가용 좌석 감소
    Concert concert = concertRepository.findById(concertId).get();
    concert.decreaseAvailableSeats();
    Concert updatedConcert = concertRepository.save(concert);
    
    // 3. 매진 체크
    if (updatedConcert.isSoldOut()) {
        registerSoldOutRankingAfterCommit(updatedConcert);
    }
    
    return confirmed;
}

// 트랜잭션 커밋 후 랭킹 등록
private void registerSoldOutRankingAfterCommit(Concert concert) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Concert freshConcert = concertRepository.findById(concertId).get();
                rankingService.registerSoldOutConcert(freshConcert);
            }
        }
    );
}
```

### 6.2 Optimistic Locking 처리

**문제**
- 여러 좌석 동시 확정 시 Concert 엔티티 version 충돌

**해결책**
```java
@Transactional
public Concert save(Concert concert) {
    if (concert.getId() != null) {
        // DB에서 최신 엔티티 조회 (version 보존)
        ConcertEntity existing = jpaRepository.findById(concert.getId()).get();
        updateEntity(existing, concert); // version 유지
        return toDomain(jpaRepository.save(existing));
    }
    return toDomain(jpaRepository.save(toEntity(concert)));
}

private void updateEntity(ConcertEntity entity, Concert concert) {
    entity.setAvailableSeats(concert.getAvailableSeats());
    entity.setStatus(concert.getStatus());
    entity.setSoldOutAt(concert.getSoldOutAt());
    // version은 JPA가 자동 증가
}
```

***

## 7. 성능 최적화

### 7.1 Redis 성능 특성

| 작업 | 시간 복잡도 | 예상 응답 시간 |
|------|-----------|--------------|
| ZADD (등록) | O(log N) | < 1ms |
| ZRANGE (Top N) | O(log N + M) | < 5ms |
| ZRANK (순위 조회) | O(log N) | < 1ms |

### 7.2 데이터 용량 산정

```
가정:
- 일간 매진 콘서트: 100개
- concertId 길이: 평균 10 bytes
- Score: 8 bytes

계산:
- 1개 엔트리: 18 bytes
- 일간 랭킹: 18 * 100 = 1.8 KB
- 주간 랭킹: 1.8 * 7 = 12.6 KB
- 월간 랭킹: 1.8 * 30 = 54 KB
- 전체 랭킹: 1.8 * 10000 = 180 KB

총 메모리: 약 250 KB (무시할 수 있는 수준)
```

### 7.3 TTL 관리 전략

```java
// 일간: 7일 후 자동 삭제
redisTemplate.expire(dailyKey, 7, TimeUnit.DAYS);

// 주간: 30일 후 자동 삭제
redisTemplate.expire(weeklyKey, 30, TimeUnit.DAYS);

// 월간: 365일 후 자동 삭제
redisTemplate.expire(monthlyKey, 365, TimeUnit.DAYS);

// 전체: TTL 없음 (영구 보관)
```

***


## 8. 제약 사항 및 고려 사항

### 8.1 제약 사항
- 밀리초 단위 정밀도: 시스템 시간에 의존
- Redis 장애 시: 랭킹 조회 불가 (MySQL 폴백 없음)
- 히스토리 보존: Redis TTL 이후 데이터 삭제

### 8.2 향후 개선 방향
- Redis 클러스터 구성: 고가용성 확보
- 캐시 워밍: 인기 랭킹 미리 로드