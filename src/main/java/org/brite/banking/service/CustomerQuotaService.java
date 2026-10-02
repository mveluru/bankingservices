package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CustomerRateLimit;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.gateway.RateLimitProperties;
import org.brite.banking.repository.CustomerRateLimitRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Per-customer daily request quota, kept in {@code customer_rate_limits} so it survives restarts and can differ per customer: a customer's own
 * {@code maxRequestsPerDay} wins, otherwise {@code banking.rate-limit.requests-per-day} applies. Also counts the customer's logins per day.
 * The customer is the one the verified token names, never a header the caller chose.
 */
@Service
@RequiredArgsConstructor
public class CustomerQuotaService {
    private final CustomerRateLimitRepository repository;
    private final RateLimitProperties rateLimitProperties;

    /**
     * Counts one request for {@code customerId} against today's limit. The remaining figure is read just before the count was taken, so it can
     * be off by the requests that were running at the same moment; the limit itself is enforced atomically in the database.
     */
    public RateLimitDecision consumeRequest(Long customerId) {
        LocalDate today = LocalDate.now();
        CustomerRateLimit row = repository.findForToday(customerId, today);
        int limit = row.getMaxRequestsPerDay() != null ? row.getMaxRequestsPerDay() : rateLimitProperties.getRequestsPerDay();
        boolean allowed = repository.tryConsumeRequest(customerId, today, limit);
        int remaining = allowed ? Math.max(0, limit - row.getRequestCount() - 1) : 0;
        return new RateLimitDecision(allowed, limit, remaining);
    }

    /** Counts one successful login for the customer today. */
    public void recordLogin(Long customerId) {
        LocalDate today = LocalDate.now();
        repository.findForToday(customerId, today);
        repository.recordLogin(customerId, today);
    }
}
