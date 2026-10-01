package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/** A freshly signed access token and when it stops being valid. The token itself is a credential: never log it. */
@Getter
@AllArgsConstructor
@ToString(exclude = "token")
public class IssuedToken {
    private final String token;
    private final Instant expiresAt;
    private final long expiresInSeconds;
}
