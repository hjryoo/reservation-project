package kr.hhplus.be.server.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class RedisTestContainerConfig {

    private static final String REDIS_IMAGE = "redis:7.2-alpine";
    private static final int REDIS_PORT = 6379;

    private static GenericContainer<?> redisContainer;

    @Bean
    public GenericContainer<?> redisContainer() {
        if (redisContainer == null) {
            redisContainer = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT)
                    .withCommand("redis-server",
                            "--maxmemory", "256mb",
                            "--maxmemory-policy", "allkeys-lru")
                    .withReuse(true); // 컨테이너 재사용으로 테스트 속도 향상

            redisContainer.start();

            // 시스템 프로퍼티 설정
            System.setProperty("spring.data.redis.host", redisContainer.getHost());
            System.setProperty("spring.data.redis.port",
                    redisContainer.getMappedPort(REDIS_PORT).toString());
        }

        return redisContainer;
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        if (redisContainer != null && redisContainer.isRunning()) {
            registry.add("spring.data.redis.host", redisContainer::getHost);
            registry.add("spring.data.redis.port",
                    () -> redisContainer.getMappedPort(REDIS_PORT));
        }
    }
}
