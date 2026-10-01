package org.brite.banking.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.IssuedToken;
import org.brite.banking.domain.TokenClaims;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.rules.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and verifies the access tokens returned by the login endpoints: HS256 JWTs carrying the issuer,
 * subject (employee number / customer id), a {@code type} ({@code employee} or {@code customer}), the
 * employee's {@code role}, a unique {@code jti} and an expiry. There are no refresh tokens: when it expires
 * the user logs in again. Tokens are never logged.
 * <p>
 * The signing secret comes from {@code banking.jwt.secret} ({@code BANKING_JWT_SECRET}); if it is blank a random
 * key is generated at startup (tokens then die with the process). Nothing in the app requires a token yet:
 * {@link #parse} is how a future filter would verify one.
 */
@Slf4j
@Service
public class JwtService {
    static final String TYPE_EMPLOYEE = "employee";
    static final String TYPE_CUSTOMER = "customer";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final String issuer;
    private final Duration lifetime;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * @throws IllegalArgumentException if the secret is set but shorter than 32 characters, or the lifetime isn't positive
     */
    JwtService(JwtProperties properties, Clock clock) {
        if (properties.getExpirationMinutes() <= 0) {
            throw new IllegalArgumentException(BankingMessages.JWT_EXPIRATION_INVALID);
        }
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            this.key = Jwts.SIG.HS256.key().build();
            log.warn(BankingMessages.LOG_JWT_EPHEMERAL_KEY);
        } else if (secret.length() < 32) {
            throw new IllegalArgumentException(BankingMessages.JWT_SECRET_TOO_SHORT);
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.issuer = properties.getIssuer();
        this.lifetime = Duration.ofMinutes(properties.getExpirationMinutes());
        this.clock = clock;
    }

    public IssuedToken issueEmployeeToken(Employee employee) {
        return issue(TYPE_EMPLOYEE, employee.getEmployeeNumber(), employee.getRole());
    }

    public IssuedToken issueCustomerToken(AuthenticatedCustomer customer) {
        return issue(TYPE_CUSTOMER, String.valueOf(customer.getCustomerId()), null);
    }

    /**
     * Verifies the signature (HMAC only; {@code alg: none} and other algorithms are refused), issuer and expiry.
     *
     * @throws InvalidTokenException (mapped to 401) for any token that isn't valid right now
     */
    public TokenClaims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException(BankingMessages.INVALID_TOKEN);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String type = claims.get(CLAIM_TYPE, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            if (!TYPE_EMPLOYEE.equals(type) && !TYPE_CUSTOMER.equals(type)) {
                throw new InvalidTokenException(BankingMessages.INVALID_TOKEN);
            }
            return TokenClaims.builder()
                    .type(type)
                    .subject(claims.getSubject())
                    .role(role == null ? null : EmployeeRole.valueOf(role))
                    .tokenId(claims.getId())
                    .issuedAt(claims.getIssuedAt().toInstant())
                    .expiresAt(claims.getExpiration().toInstant())
                    .build();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn(BankingMessages.LOG_TOKEN_REJECTED, e.getClass().getSimpleName());
            throw new InvalidTokenException(BankingMessages.INVALID_TOKEN);
        }
    }

    private IssuedToken issue(String type, String subject, EmployeeRole role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(lifetime);
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (role != null) {
            builder.claim(CLAIM_ROLE, role.name());
        }
        String token = builder.signWith(key, Jwts.SIG.HS256).compact();
        log.info(BankingMessages.LOG_TOKEN_ISSUED, type, subject, expiresAt);
        return new IssuedToken(token, expiresAt, lifetime.toSeconds());
    }
}
