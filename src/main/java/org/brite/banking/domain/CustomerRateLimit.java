package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** A customer's daily request limit and today's usage (see {@code customer_rate_limits}). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRateLimit {
    private Long customerId;
    /** This customer's own cap per day; null = the application default ({@code banking.rate-limit.requests-per-day}). */
    private Integer maxRequestsPerDay;
    private LocalDate usageDate;
    private int requestCount;
    private int loginCount;
}
