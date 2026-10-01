package org.brite.banking.rules;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backs banking.jwt.* in application.yml: how login tokens are signed and how long they last.
 * The secret comes from the environment ({@code BANKING_JWT_SECRET}), never from a committed file.
 */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "banking.jwt")
public class JwtProperties {
    /**
     * HMAC-SHA256 signing secret, at least 32 characters (generate one with {@code openssl rand -base64 48}).
     * Blank = a random key is generated at startup, so tokens stop working after every restart.
     */
    private String secret;
    private String issuer = "bankingservices";
    private int expirationMinutes = 30;
}
