package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CustomerRateLimit;
import org.brite.banking.domain.CustomerRateLimitView;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.exception.CustomerNotFoundException;
import org.brite.banking.gateway.RateLimitProperties;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.CustomerRateLimitRepository;
import org.brite.banking.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

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

    /**
     * The customer's limit and today's usage, for a manager.
     *
     * @throws CustomerNotFoundException (mapped to 404) if the customer doesn't exist
     */
    public CustomerRateLimitView view(Long customerId) {
        requireCustomer(customerId);
        LocalDate today = LocalDate.now();
        CustomerRateLimit row = repository.find(customerId).orElse(null);
        Integer custom = row != null ? row.getMaxRequestsPerDay() : null;
        int defaultLimit = rateLimitProperties.getRequestsPerDay();
        int limit = custom != null ? custom : defaultLimit;
        boolean countedToday = row != null && today.equals(row.getUsageDate());
        int requests = countedToday ? row.getRequestCount() : 0;
        return CustomerRateLimitView.builder().customerId(customerId).dailyLimit(limit).customLimit(custom).defaultLimit(defaultLimit)
                .usageDate(today).requestsToday(requests).remainingToday(Math.max(0, limit - requests))
                .loginsToday(countedToday ? row.getLoginCount() : 0).build();
    }

    /**
     * Gives the customer their own daily limit, or with {@code null} puts them back on the application default; today's usage is untouched.
     * The range (1 to 1,000,000) is checked on the request.
     *
     * @throws CustomerNotFoundException (mapped to 404) if the customer doesn't exist
     */
    public CustomerRateLimitView setLimit(Long customerId, Integer maxRequestsPerDay) {
        requireCustomer(customerId);
        repository.setMaxRequestsPerDay(customerId, LocalDate.now(), maxRequestsPerDay);
        return view(customerId);
    }

    private void requireCustomer(Long customerId) {
        customerRepository.findIdentityById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.format(BankingMessages.CUSTOMER_NOT_FOUND, customerId)));
    }
}
