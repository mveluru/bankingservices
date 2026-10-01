package org.brite.banking.domain;

import java.time.LocalDateTime;

/** The part of a stored login (employee or customer) that password checking and status changes read and update. */
public interface LoginState {
    String getPasswordHash();

    int getFailedAttempts();

    void setFailedAttempts(int failedAttempts);

    LocalDateTime getLockedUntil();

    void setLockedUntil(LocalDateTime lockedUntil);

    void setLastLoginAt(LocalDateTime lastLoginAt);

    void setPasswordHash(String passwordHash);

    LocalDateTime getPasswordChangedAt();

    void setPasswordChangedAt(LocalDateTime passwordChangedAt);

    /** Wrong security answers given to the password reset since the last successful reset. */
    int getResetFailedAttempts();

    void setResetFailedAttempts(int resetFailedAttempts);

    /** Password reset is refused until this moment after too many wrong answers; null when not locked. */
    LocalDateTime getResetLockedUntil();

    void setResetLockedUntil(LocalDateTime resetLockedUntil);

    LoginStatus getStatus();

    void setStatus(LoginStatus status);

    String getStatusReason();

    void setStatusReason(String statusReason);

    void setStatusChangedAt(LocalDateTime statusChangedAt);

    /**
     * The status that applies right now: a {@code LOCKED} login whose {@code lockedUntil} has passed counts as
     * {@code ACTIVE} again, even if nobody has logged in since to clear it.
     */
    default LoginStatus effectiveStatus(LocalDateTime now) {
        if (getStatus() == LoginStatus.LOCKED && getLockedUntil() != null && !getLockedUntil().isAfter(now)) {
            return LoginStatus.ACTIVE;
        }
        return getStatus();
    }
}
