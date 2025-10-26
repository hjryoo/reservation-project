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

/**
 * 분산락 AOP
 *
 * 중요: 락 획득 → 트랜잭션 시작 순서를 보장하기 위해
 * @Transactional보다 먼저 실행되어야 합니다.
 */
@Slf4j
@Aspect
@Component
@Order(1) // @Transactional(Order=2)보다 먼저 실행
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(kr.hhplus.be.server.infrastructure.lock.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // SpEL을 사용하여 동적 키 생성
        String key = generateKey(distributedLock.key(), signature, joinPoint.getArgs());
        RLock lock = redissonClient.getLock(key);

        boolean isLocked = false;
        try {
            // 락 획득 시도
            isLocked = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!isLocked) {
                log.warn("Failed to acquire lock: {}", key);
                throw new IllegalStateException("락 획득 실패: " + key);
            }

            log.debug("Lock acquired: {}", key);

            // 비즈니스 로직 실행
            return joinPoint.proceed();

        } finally {
            // 락 해제
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: {}", key);
            }
        }
    }

    /**
     * SpEL을 사용하여 동적 키 생성
     */
    private String generateKey(String keyExpression, MethodSignature signature, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 SpEL 컨텍스트에 등록
        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
