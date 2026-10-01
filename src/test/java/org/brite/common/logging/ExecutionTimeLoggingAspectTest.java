package org.brite.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Spring context) for the AOP advice logic itself: it exercises
 * ExecutionTimeLoggingAspect.logExecutionTime() directly against a mocked
 * ProceedingJoinPoint, since the pointcut wiring (matching @RestController methods) is
 * a Spring/AspectJ weaving concern that would need a full application context to verify.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionTimeLoggingAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Signature signature;

    private final ExecutionTimeLoggingAspect aspect = new ExecutionTimeLoggingAspect();

    @BeforeEach
    void setUp() {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("SomeController.someMethod(..)");
    }

    @Test
    void logExecutionTime_delegatesToJoinPointAndReturnsItsResult() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.logExecutionTime(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void logExecutionTime_propagatesExceptionsFromTheAdvisedMethod() throws Throwable {
        RuntimeException failure = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.logExecutionTime(joinPoint))
                .isSameAs(failure);

        verify(joinPoint).proceed();
    }
}
