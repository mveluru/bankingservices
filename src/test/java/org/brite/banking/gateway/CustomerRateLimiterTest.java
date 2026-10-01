package org.brite.banking.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context) for the in-memory per-customer daily counter.
 */
class CustomerRateLimiterTest {

    private CustomerRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = RateLimitProperties.builder()
                .enabled(true)
                .requestsPerDay(3)
                .customerHeaderName("X-Customer-Id")
                .build();
        rateLimiter = new CustomerRateLimiter(properties);
    }

    @Test
    void tryConsume_belowLimit_returnsDecreasingRemainingCount() {
        assertThat(rateLimiter.tryConsume("cust-1")).isEqualTo(2);
        assertThat(rateLimiter.tryConsume("cust-1")).isEqualTo(1);
        assertThat(rateLimiter.tryConsume("cust-1")).isEqualTo(0);
    }

    @Test
    void tryConsume_pastConfiguredLimit_returnsNegativeOne() {
        rateLimiter.tryConsume("cust-1");
        rateLimiter.tryConsume("cust-1");
        rateLimiter.tryConsume("cust-1");

        assertThat(rateLimiter.tryConsume("cust-1")).isEqualTo(-1);
    }

    @Test
    void tryConsume_differentCustomers_haveIndependentCounters() {
        rateLimiter.tryConsume("cust-1");
        rateLimiter.tryConsume("cust-1");
        rateLimiter.tryConsume("cust-1");
        // cust-1 is now exhausted; cust-2 should be unaffected
        assertThat(rateLimiter.tryConsume("cust-1")).isEqualTo(-1);
        assertThat(rateLimiter.tryConsume("cust-2")).isEqualTo(2);
    }
}
