package kr.hhplus.be.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Configuration
@EnableCaching
public class RedisCacheConfig extends CachingConfigurerSupport {

    /**
     * ObjectMapper 설정 (LocalDateTime 직렬화 지원)
     */
    @Bean
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return mapper;
    }

    /**
     * Redis Cache Manager 설정
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     ObjectMapper cacheObjectMapper) {

        // 기본 캐시 설정 (5분 TTL)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper)))
                .disableCachingNullValues() // null 값은 캐시하지 않음
                .computePrefixWith(cacheName -> "hhplus:" + cacheName + "::"); // 캐시 키 prefix

        // 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // ⭐ 좌석 현황 (30초) - 가장 중요!
        cacheConfigurations.put("seatAvailability",
                defaultConfig.entryTtl(Duration.ofSeconds(30)));

        // 콘서트 목록 (5분)
        cacheConfigurations.put("concertAvailable",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 콘서트 상세 (30분)
        cacheConfigurations.put("concertDetail",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 콘서트 날짜 (10분)
        cacheConfigurations.put("concertDates",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // ⭐ 대기열 순위 (10초)
        cacheConfigurations.put("queuePosition",
                defaultConfig.entryTtl(Duration.ofSeconds(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // 트랜잭션과 통합
                .build();
    }

    /**
     * 캐시 에러 핸들러 (Redis 장애 시 서비스 중단 방지)
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception,
                                            org.springframework.cache.Cache cache,
                                            Object key) {
                log.warn("캐시 조회 실패 - Cache: {}, Key: {}", cache.getName(), key, exception);
                // 캐시 실패 시 DB에서 조회하도록 예외를 무시
            }

            @Override
            public void handleCachePutError(RuntimeException exception,
                                            org.springframework.cache.Cache cache,
                                            Object key, Object value) {
                log.warn("캐시 저장 실패 - Cache: {}, Key: {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception,
                                              org.springframework.cache.Cache cache,
                                              Object key) {
                log.warn("캐시 삭제 실패 - Cache: {}, Key: {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception,
                                              org.springframework.cache.Cache cache) {
                log.warn("캐시 전체 삭제 실패 - Cache: {}", cache.getName(), exception);
            }
        };
    }
}