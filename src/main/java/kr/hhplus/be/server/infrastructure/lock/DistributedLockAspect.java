package kr.hhplus.be.server.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    private static final long DEFAULT_TIMEOUT_THRESHOLD_MS = 3000L;

    @Around("@annotation(kr.hhplus.be.server.infrastructure.lock.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String key = generateKey(distributedLock.key(), signature, joinPoint.getArgs());
        RLock lock = redissonClient.getLock(key);

        boolean isLocked = false;
        long startTime = System.currentTimeMillis();

        try {
            // Watch Dog 활성화: leaseTime = -1
            // 비즈니스 로직이 완료될 때까지 자동으로 락 갱신
            isLocked = lock.tryLock(
                    distributedLock.waitTime(),
                    -1, // leaseTime = -1: Watch Dog 활성화
                    distributedLock.timeUnit()
            );

            if (!isLocked) {
                log.warn("락 획득 실패: key={}, waitTime={}{}",
                        key,
                        distributedLock.waitTime(),
                        distributedLock.timeUnit());
                throw new IllegalStateException("락 획득 실패: " + key);
            }

            log.debug("락 획득 성공: key={}", key);

            // 비즈니스 로직 실행
            Object result = joinPoint.proceed();

            // 실행 시간 모니터링
            long duration = System.currentTimeMillis() - startTime;
            monitorExecutionTime(key, duration, method);

            return result;

        } finally {
            // 락 해제
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                long totalDuration = System.currentTimeMillis() - startTime;
                log.debug("락 해제: key={}, 총 소요시간={}ms", key, totalDuration);
            }
        }
    }

    /**
     * SpEL을 사용하여 동적 키 생성
     */
    private String generateKey(String keyExpression, MethodSignature signature, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }

    /**
     * 비즈니스 로직 실행 시간 모니터링
     *
     * 무한루프 등 이상 상황 감지를 위한 모니터링
     * 정상 범위를 벗어난 경우 경고 로그 출력
     */
    private void monitorExecutionTime(String key, long duration, Method method) {
        if (duration > DEFAULT_TIMEOUT_THRESHOLD_MS) {
            log.warn("비즈니스 로직 실행 시간 초과: key={}, duration={}ms, method={}, " +
                            "정상 범위({}ms)를 벗어났습니다. DB 응답 지연이나 네트워크 문제를 확인하세요.",
                    key, duration, method.getName(), DEFAULT_TIMEOUT_THRESHOLD_MS);
        }
    }
}