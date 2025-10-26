package kr.hhplus.be.server.infrastructure.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 분산락 어노테이션
 * 메서드 실행 전 락을 획득하고, 실행 후 락을 해제합니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키 (SpEL 지원)
     * 예: "seat:reservation:#{#concertId}:#{#seatNumber}"
     */
    String key();

    /**
     * 락 대기 시간 (기본 5초)
     */
    long waitTime() default 5L;

    /**
     * 락 점유 시간 (기본 3초)
     */
    long leaseTime() default 3L;

    /**
     * 시간 단위 (기본 초)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
