package org.brite.banking.domain;

/** Employment state; only {@code ACTIVE} employees should be allowed to act on accounts. */
public enum EmployeeStatus {
    ACTIVE,
    ON_LEAVE,
    TERMINATED
}
