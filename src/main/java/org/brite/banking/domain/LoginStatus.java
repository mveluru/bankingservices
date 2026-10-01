package org.brite.banking.domain;

/**
 * State of an employee or customer login. Only {@code ACTIVE} may perform transactions.
 * {@code LOCKED} is set automatically after too many wrong passwords (it expires at {@code lockedUntil})
 * or by an administrator (no expiry); {@code INACTIVE} and {@code SUSPENDED} are set by an administrator
 * and last until someone sets the login back to {@code ACTIVE}.
 */
public enum LoginStatus {
    ACTIVE,
    INACTIVE,
    LOCKED,
    SUSPENDED
}
