package org.brite.banking.rules;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backs banking.password-reset.*: wrong security answers allowed before password reset locks for that login, and for how long.
 * Security answers are guessable, so this lock (separate from the login lock) is what protects the reset.
 */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "banking.password-reset")
public class PasswordResetProperties {
    private int maxFailedAttempts = 3;
    private int lockoutMinutes = 30;
}
