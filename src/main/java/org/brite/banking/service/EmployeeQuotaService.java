package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.EmployeeRateLimit;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.exception.EmployeeNotFoundException;
import org.brite.banking.gateway.RateLimitProperties;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.EmployeeRateLimitRepository;
import org.brite.banking.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Per-employee daily request quota (the staff counterpart of {@link CustomerQuotaService}), kept in {@code employee_rate_limits}: an employee's
 * own {@code maxRequestsPerDay} wins, otherwise {@code banking.rate-limit.employee-requests-per-day} applies. Also counts the employee's logins
 * per day. The employee is the one the verified token names, never a header the caller chose.
 */
@Service
@RequiredArgsConstructor
public class EmployeeQuotaService {
    private final EmployeeRateLimitRepository repository;
    private final RateLimitProperties rateLimitProperties;
    private final EmployeeRepository employeeRepository;

    /**
     * Counts one request for {@code employeeNumber} against today's limit. The remaining figure is read just before the count was taken, so it
     * can be off by the requests that were running at the same moment; the limit itself is enforced atomically in the database.
     */
    public RateLimitDecision consumeRequest(String employeeNumber) {
        LocalDate today = LocalDate.now();
        EmployeeRateLimit row = repository.findForToday(employeeNumber, today);
        int limit = row.getMaxRequestsPerDay() != null ? row.getMaxRequestsPerDay() : rateLimitProperties.getEmployeeRequestsPerDay();
        boolean allowed = repository.tryConsumeRequest(employeeNumber, today, limit);
        int remaining = allowed ? Math.max(0, limit - row.getRequestCount() - 1) : 0;
        return new RateLimitDecision(allowed, limit, remaining);
    }

    /** Counts one successful login for the employee today. */
    public void recordLogin(String employeeNumber) {
        LocalDate today = LocalDate.now();
        repository.findForToday(employeeNumber, today);
        repository.recordLogin(employeeNumber, today);
    }

    /**
     * The employee's limit and today's usage, for an administrator.
     *
     * @throws EmployeeNotFoundException (mapped to 404) if the employee doesn't exist
     */
    public EmployeeRateLimitView view(String employeeNumber) {
        requireEmployee(employeeNumber);
        LocalDate today = LocalDate.now();
        EmployeeRateLimit row = repository.find(employeeNumber).orElse(null);
        Integer custom = row != null ? row.getMaxRequestsPerDay() : null;
        int defaultLimit = rateLimitProperties.getEmployeeRequestsPerDay();
        int limit = custom != null ? custom : defaultLimit;
        boolean countedToday = row != null && today.equals(row.getUsageDate());
        int requests = countedToday ? row.getRequestCount() : 0;
        return EmployeeRateLimitView.builder().employeeNumber(employeeNumber).dailyLimit(limit).customLimit(custom).defaultLimit(defaultLimit)
                .usageDate(today).requestsToday(requests).remainingToday(Math.max(0, limit - requests))
                .loginsToday(countedToday ? row.getLoginCount() : 0).build();
    }

    /**
     * Gives the employee their own daily limit, or with {@code null} puts them back on the application default; today's usage is untouched.
     * The range (1 to 1,000,000) is checked on the request.
     *
     * @throws EmployeeNotFoundException (mapped to 404) if the employee doesn't exist
     */
    public EmployeeRateLimitView setLimit(String employeeNumber, Integer maxRequestsPerDay) {
        requireEmployee(employeeNumber);
        repository.setMaxRequestsPerDay(employeeNumber, LocalDate.now(), maxRequestsPerDay);
        return view(employeeNumber);
    }

    private void requireEmployee(String employeeNumber) {
        employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.EMPLOYEE_NOT_FOUND, employeeNumber)));
    }
}
