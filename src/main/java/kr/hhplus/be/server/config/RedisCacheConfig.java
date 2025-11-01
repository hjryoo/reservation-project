package kr.hhplus.be.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
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

/**
 * Redis 캐시 설정
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisCacheConfig {

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
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "hhplus:" + cacheName + "::");

        // 캐시별 개별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put("seatAvailability",
                defaultConfig.entryTtl(Duration.ofSeconds(30)));

        cacheConfigurations.put("concertAvailable",
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        cacheConfigurations.put("concertDetail",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        cacheConfigurations.put("concertDates",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        cacheConfigurations.put("queuePosition",
                defaultConfig.entryTtl(Duration.ofSeconds(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception,
                                            org.springframework.cache.Cache cache,
                                            Object key) {
                log.warn("캐시 조회 실패 - Cache: {}, Key: {}", cache.getName(), key, exception);
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