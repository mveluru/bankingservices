package org.brite.banking.domain;

/** Outcome of counting one request against a customer's daily limit: whether it may proceed, the limit that applied and what is left. */
public record RateLimitDecision(boolean allowed, int limit, int remaining) {
}
