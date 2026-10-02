package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.EmployeeRateLimit;
import org.brite.banking.entity.EmployeeRateLimitEntity;
import org.brite.banking.repository.jpa.EmployeeRateLimitJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Facade over {@code employee_rate_limits}: one row per employee with their own daily limit and today's counters (the staff counterpart of
 * {@link CustomerRateLimitRepository}). Deliberately not transactional as a whole: each call is its own short transaction, so losing the race
 * to create an employee's first row (a unique violation) leaves nothing marked for rollback and the row the other request created is read.
 */
@Repository
@RequiredArgsConstructor
public class EmployeeRateLimitRepository {
    private final EmployeeRateLimitJpaRepository jpaRepository;

    /** The employee's row with its counters belonging to {@code today}: created on first use, and started again after a day change. */
    public EmployeeRateLimit findForToday(String employeeNumber, LocalDate today) {
        EmployeeRateLimitEntity entity = jpaRepository.findByEmployeeNumber(employeeNumber).orElse(null);
        if (entity == null) {
            try {
                jpaRepository.saveAndFlush(EmployeeRateLimitEntity.builder().employeeNumber(employeeNumber).usageDate(today).build());
            } catch (DataIntegrityViolationException createdByAnotherRequest) {
                // the unique index on employee_number: another request created it first
            }
        } else if (!today.equals(entity.getUsageDate())) {
            jpaRepository.startNewDay(employeeNumber, today);
        }
        return toDomain(jpaRepository.findByEmployeeNumber(employeeNumber).orElseThrow());
    }

    /** Counts one request unless today's limit is already reached; false means the limit was reached. */
    public boolean tryConsumeRequest(String employeeNumber, LocalDate today, int limit) {
        return jpaRepository.consumeRequest(employeeNumber, today, limit) == 1;
    }

    public void recordLogin(String employeeNumber, LocalDate today) {
        jpaRepository.addLogin(employeeNumber, today);
    }

    /** Sets (or, with null, clears) the employee's own daily limit; the row is created first if the employee has none yet. */
    public void setMaxRequestsPerDay(String employeeNumber, LocalDate today, Integer max) {
        findForToday(employeeNumber, today);
        jpaRepository.setMaxRequestsPerDay(employeeNumber, max);
    }

    public Optional<EmployeeRateLimit> find(String employeeNumber) {
        return jpaRepository.findByEmployeeNumber(employeeNumber).map(this::toDomain);
    }

    private EmployeeRateLimit toDomain(EmployeeRateLimitEntity entity) {
        return EmployeeRateLimit.builder().employeeNumber(entity.getEmployeeNumber()).maxRequestsPerDay(entity.getMaxRequestsPerDay())
                .usageDate(entity.getUsageDate()).requestCount(entity.getRequestCount()).loginCount(entity.getLoginCount()).build();
    }
}
