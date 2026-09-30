package org.bee.banking.domain;

public enum AccountStatus {
    ACTIVE,
    /** Temporarily blocked: no withdraw/deposit until reactivated (or the suspension end passes). */
    SUSPENDED,
    CLOSED
}
