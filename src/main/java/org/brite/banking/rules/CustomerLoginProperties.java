package org.brite.banking.rules;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Backs banking.customer-login.*: wrong passwords allowed before a customer login locks, and for how long. */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "banking.customer-login")
public class CustomerLoginProperties {
    private int maxFailedAttempts = 5;
    private int lockoutMinutes = 15;
}
