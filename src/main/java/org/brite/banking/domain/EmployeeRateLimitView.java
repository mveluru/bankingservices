package org.brite.banking.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * What an administrator sees of an employee's daily request limit: the limit that applies today, whether it is the employee's own, and today's
 * usage (zero for an employee who has made no request today).
 */
@Getter
@Builder
public class EmployeeRateLimitView {
    private String employeeNumber;
    /** The limit that applies: the employee's own if set, else the default. */
    private int dailyLimit;
    /** The employee's own limit; null when they are on the default. */
    private Integer customLimit;
    private int defaultLimit;
    private LocalDate usageDate;
    private int requestsToday;
    private int remainingToday;
    private int loginsToday;
}
