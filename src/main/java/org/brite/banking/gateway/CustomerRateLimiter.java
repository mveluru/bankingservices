package org.brite.banking.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory, day-rolling per-customer request counter (mirrors the daily-limit pattern
 * already used by NotificationService for SMS). Counts reset for every customer at once
 * when the wall-clock date changes; there is no per-customer TTL bookkeeping since this
 * is a single-instance in-memory mock, not a distributed rate limiter.
 */
@Component
@RequiredArgsConstructor
public class CustomerRateLimiter {
    private final RateLimitProperties rateLimitProperties;

    private final Map<String, AtomicInteger> requestCountsByCustomer = new ConcurrentHashMap<>();
    private volatile LocalDate currentDay = LocalDate.now();

    /**
     * Atomically checks and increments today's request count for a customer.
     *
     * @return remaining requests allowed for the rest of the day, or -1 if the
     *         customer has already reached the configured daily limit
     */
    public synchronized int tryConsume(String customerId) {
        rolloverIfNewDay();
        int limit = rateLimitProperties.getRequestsPerDay();
        AtomicInteger count = requestCountsByCustomer.computeIfAbsent(customerId, key -> new AtomicInteger(0));
        if (count.get() >= limit) {
            return -1;
        }
        int used = count.incrementAndGet();
        return limit - used;
    }

    private void rolloverIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            currentDay = today;
            requestCountsByCustomer.clear();
        }
    }
}
