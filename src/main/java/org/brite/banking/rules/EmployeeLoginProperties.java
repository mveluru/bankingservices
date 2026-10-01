package org.brite.banking.rules;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backs banking.employee-login.* in application.yml: how many wrong passwords lock an employee
 * login and for how long. An 8-digit password has only 10^8 combinations, so the lock is what
 * makes guessing impractical.
 */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "banking.employee-login")
public class EmployeeLoginProperties {
    private int maxFailedAttempts = 5;
    private int lockoutMinutes = 15;
}
