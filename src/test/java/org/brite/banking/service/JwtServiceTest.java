package org.brite.banking.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.IssuedToken;
import org.brite.banking.domain.TokenClaims;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.rules.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-that-is-at-least-32-chars";
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private JwtService service;

    private static JwtProperties properties(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setIssuer("bankingservices");
        p.setExpirationMinutes(30);
        return p;
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        service = new JwtService(properties(SECRET), clockAt(NOW));
    }

    private Employee teller() {
        return Employee.builder().employeeNumber("EMP-000010").role(EmployeeRole.TELLER).build();
    }

    @Test
    void employeeTokenCarriesSubjectTypeRoleAndAThirtyMinuteExpiry() {
        IssuedToken issued = service.issueEmployeeToken(teller());

        assertEquals(1800, issued.getExpiresInSeconds());
        assertEquals(NOW.plusSeconds(1800), issued.getExpiresAt());
        TokenClaims claims = service.parse(issued.getToken());
        assertEquals("employee", claims.getType());
        assertEquals("EMP-000010", claims.getSubject());
        assertEquals(EmployeeRole.TELLER, claims.getRole());
        assertEquals(NOW.plusSeconds(1800), claims.getExpiresAt());
        assertNotNull(claims.getTokenId());
    }

    @Test
    void customerTokenCarriesTheCustomerIdAndNoRole() {
        IssuedToken issued = service.issueCustomerToken(AuthenticatedCustomer.builder().customerId(5L).firstName("Alice").lastName("Smith").build());

        TokenClaims claims = service.parse(issued.getToken());
        assertEquals("customer", claims.getType());
        assertEquals("5", claims.getSubject());
        assertNull(claims.getRole());
    }

    @Test
    void tokenHoldsNoPersonalDataOrPasswordMaterial() {
        String payload = new String(Base64.getUrlDecoder().decode(service.issueEmployeeToken(teller()).getToken().split("\\.")[1]), StandardCharsets.UTF_8);
        assertFalse(payload.toLowerCase().contains("password"));
        assertFalse(payload.contains("email"));
        assertFalse(payload.contains("phone"));
    }

    @Test
    void everyTokenHasItsOwnId() {
        assertNotEquals(service.parse(service.issueEmployeeToken(teller()).getToken()).getTokenId(),
                service.parse(service.issueEmployeeToken(teller()).getToken()).getTokenId());
    }

    @Test
    void aTokenIsRejectedOnceItHasExpired() {
        String token = service.issueEmployeeToken(teller()).getToken();

        JwtService later = new JwtService(properties(SECRET), clockAt(NOW.plus(Duration.ofMinutes(31))));
        assertThrows(InvalidTokenException.class, () -> later.parse(token));
        JwtService justBefore = new JwtService(properties(SECRET), clockAt(NOW.plus(Duration.ofMinutes(29))));
        assertEquals("EMP-000010", justBefore.parse(token).getSubject());
    }

    @Test
    void aTamperedPayloadOrSignatureIsRejected() {
        String token = service.issueEmployeeToken(teller()).getToken();
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"bankingservices\",\"sub\":\"EMP-000001\",\"type\":\"employee\",\"role\":\"AREA_MANAGER\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(InvalidTokenException.class, () -> service.parse(parts[0] + "." + forgedPayload + "." + parts[2]));
        assertThrows(InvalidTokenException.class, () -> service.parse(parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "A"));
    }

    @Test
    void aTokenSignedWithAnotherKeyOrIssuerIsRejected() {
        SecretKey other = Keys.hmacShaKeyFor("a-completely-different-secret-of-32-chars!".getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder().issuer("bankingservices").subject("EMP-000001").claim("type", "employee")
                .expiration(Date.from(NOW.plusSeconds(600))).signWith(other, Jwts.SIG.HS256).compact();
        assertThrows(InvalidTokenException.class, () -> service.parse(foreign));

        SecretKey ours = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String wrongIssuer = Jwts.builder().issuer("someone-else").subject("EMP-000001").claim("type", "employee")
                .expiration(Date.from(NOW.plusSeconds(600))).signWith(ours, Jwts.SIG.HS256).compact();
        assertThrows(InvalidTokenException.class, () -> service.parse(wrongIssuer));
    }

    @Test
    void anUnsignedAlgNoneTokenIsRejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"bankingservices\",\"sub\":\"EMP-000001\",\"type\":\"employee\",\"role\":\"AREA_MANAGER\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidTokenException.class, () -> service.parse(header + "." + payload + "."));
        assertThrows(InvalidTokenException.class, () -> service.parse(header + "." + payload));
    }

    @Test
    void aSignedTokenOfAnUnknownTypeIsRejected() {
        SecretKey ours = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().issuer("bankingservices").subject("x").claim("type", "robot")
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(600))).signWith(ours, Jwts.SIG.HS256).compact();
        assertThrows(InvalidTokenException.class, () -> service.parse(token));
    }

    @Test
    void garbageAndBlankTokensAreRejected() {
        for (String bad : new String[]{null, "", "  ", "abc", "a.b.c", "not.a.jwt.at.all"}) {
            assertThrows(InvalidTokenException.class, () -> service.parse(bad), String.valueOf(bad));
        }
    }

    @Test
    void aSecretUnderThirtyTwoCharactersAndANonPositiveLifetimeAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(properties("too-short"), clockAt(NOW)));
        JwtProperties p = properties(SECRET);
        p.setExpirationMinutes(0);
        assertThrows(IllegalArgumentException.class, () -> new JwtService(p, clockAt(NOW)));
    }

    @Test
    void aBlankSecretGivesARandomKeyPerInstance() {
        JwtService a = new JwtService(properties(""), clockAt(NOW));
        JwtService b = new JwtService(properties(null), clockAt(NOW));
        String token = a.issueEmployeeToken(teller()).getToken();

        assertEquals("EMP-000010", a.parse(token).getSubject());
        assertThrows(InvalidTokenException.class, () -> b.parse(token));
    }

    @Test
    void theIssuedTokenIsNotPrintedByToString() {
        IssuedToken issued = service.issueEmployeeToken(teller());
        assertFalse(issued.toString().contains(issued.getToken()));
    }
}
