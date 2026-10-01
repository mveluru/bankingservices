package org.bee.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Spring AOP aspect (not a hand-written filter/interceptor) that logs how long every
 * REST endpoint in the app took to execute. The pointcut matches any method inside a
 * class annotated {@code @RestController}, so it covers every controller (banking,
 * events, versioning, sample) without touching any of them - add a
 * new controller anywhere under org.bee and it's automatically covered.
 */
@Aspect
@Component
@Slf4j
public class ExecutionTimeLoggingAspect {

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} executed in {} ms", joinPoint.getSignature().toShortString(), durationMs);
        }
    }
}

