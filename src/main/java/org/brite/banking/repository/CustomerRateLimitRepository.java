package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CustomerRateLimit;
import org.brite.banking.entity.CustomerRateLimitEntity;
import org.brite.banking.repository.jpa.CustomerRateLimitJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Facade over {@code customer_rate_limits}: one row per customer with their own daily limit and today's counters. It is deliberately not
 * transactional as a whole: each call below is its own short transaction, so losing the race to create a customer's first row (a unique
 * violation) leaves nothing marked for rollback and the row the other request created is simply read.
 */
@Repository
@RequiredArgsConstructor
public class CustomerRateLimitRepository {
    private final CustomerRateLimitJpaRepository jpaRepository;

    /** The customer's row with its counters belonging to {@code today}: created on first use, and started again after a day change. */
    public CustomerRateLimit findForToday(Long customerId, LocalDate today) {
        CustomerRateLimitEntity entity = jpaRepository.findByCustomerId(customerId).orElse(null);
        if (entity == null) {
            try {
                jpaRepository.saveAndFlush(CustomerRateLimitEntity.builder().customerId(customerId).usageDate(today).build());
            } catch (DataIntegrityViolationException createdByAnotherRequest) {
                // the unique index on customer_id: another request created it first
            }
        } else if (!today.equals(entity.getUsageDate())) {
            jpaRepository.startNewDay(customerId, today);
        }
        return toDomain(jpaRepository.findByCustomerId(customerId).orElseThrow());
    }

    /** Counts one request unless today's limit is already reached; false means the limit was reached. */
    public boolean tryConsumeRequest(Long customerId, LocalDate today, int limit) {
        return jpaRepository.consumeRequest(customerId, today, limit) == 1;
    }

    public void recordLogin(Long customerId, LocalDate today) {
        jpaRepository.addLogin(customerId, today);
    }

    /** Sets (or, with null, clears) the customer's own daily limit; the row is created first if the customer has none yet. */
    public void setMaxRequestsPerDay(Long customerId, LocalDate today, Integer max) {
        findForToday(customerId, today);
        jpaRepository.setMaxRequestsPerDay(customerId, max);
    }

    public Optional<CustomerRateLimit> find(Long customerId) {
        return jpaRepository.findByCustomerId(customerId).map(this::toDomain);
    }

    private CustomerRateLimit toDomain(CustomerRateLimitEntity entity) {
        return CustomerRateLimit.builder().customerId(entity.getCustomerId()).maxRequestsPerDay(entity.getMaxRequestsPerDay())
                .usageDate(entity.getUsageDate()).requestCount(entity.getRequestCount()).loginCount(entity.getLoginCount()).build();
    }
}
