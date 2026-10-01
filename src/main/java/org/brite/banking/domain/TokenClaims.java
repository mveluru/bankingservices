package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** What a verified access token says about its holder. */
@Getter
@Builder
@AllArgsConstructor
public class TokenClaims {
    /** {@code "employee"} or {@code "customer"}. */
    private final String type;
    /** Employee number (e.g. {@code EMP-000010}) or the customer id as a string. */
    private final String subject;
    /** The employee's role; null for customers. */
    private final EmployeeRole role;
    private final String tokenId;
    private final Instant issuedAt;
    private final Instant expiresAt;
}
