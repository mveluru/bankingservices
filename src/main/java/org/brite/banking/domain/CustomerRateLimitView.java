package org.brite.banking.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * What a manager sees of a customer's daily request limit: the limit that applies today, whether it is the customer's own, and today's usage.
 * Usage is zero for a customer who has made no request today.
 */
@Getter
@Builder
public class CustomerRateLimitView {
    private Long customerId;
    /** The limit that applies: the customer's own if set, else the default. */
    private int dailyLimit;
    /** The customer's own limit; null when they are on the default. */
    private Integer customLimit;
    private int defaultLimit;
    private LocalDate usageDate;
    private int requestsToday;
    private int remainingToday;
    private int loginsToday;
}
