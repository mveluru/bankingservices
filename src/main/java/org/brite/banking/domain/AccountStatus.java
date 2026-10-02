package org.brite.banking.domain;

public enum AccountStatus {
    ACTIVE,
    /** Temporarily blocked: no withdraw/deposit until reactivated (or the suspension end passes). */
    SUSPENDED,
    CLOSED,
    /** Not in use: no withdraw/deposit, and a holder with no ACTIVE account can't sign in (they contact customer support). Set by hand (SQL) for now. */
    INACTIVE,
    /** No activity for a long time: same effect as {@link #INACTIVE}. Set by hand (SQL) for now; there is no automatic dormancy job. */
    DORMANT
}
