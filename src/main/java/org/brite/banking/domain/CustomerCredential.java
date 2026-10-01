package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A customer's login: username plus a BCrypt hash of the 8-digit password (never the password
 * itself). Kept apart from the customer profile so profile reads and responses never carry it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "passwordHash")
public class CustomerCredential implements LoginState {
    private Long customerId;
    private String username;
    private String passwordHash;
    private int failedAttempts;
    /** Login is refused until this moment after too many failures; null when not locked. */
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    @Builder.Default
    private LoginStatus status = LoginStatus.ACTIVE;
    /** Why the status was last changed by an administrator; null otherwise. */
    private String statusReason;
    private LocalDateTime statusChangedAt;
}
