package org.brite.banking.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.rules.JwtProperties;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CustomerAuthenticationFilterTest {
    private static final String SECRET = "customer-filter-test-secret-at-least-32-chars";

    private JwtService jwtService;
    private CustomerCredentialService credentials;
    private CustomerAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        jwtService = new JwtService(properties);
        credentials = mock(CustomerCredentialService.class);
        filter = new CustomerAuthenticationFilter(jwtService, credentials);
    }

    private String customerToken(long id) {
        return jwtService.issueCustomerToken(AuthenticatedCustomer.builder().customerId(id).build()).getToken();
    }

    private MockHttpServletRequest request(String method, String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/brite" + path);
        request.setContextPath("/brite");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void aValidCustomerTokenWithAnActiveLoginPassesAndTheSubjectBecomesTheCustomerId() throws Exception {
        MockHttpServletRequest request = request("POST", "/v1/api/accounts/withdraw", "Bearer " + customerToken(5));
        MockFilterChain chain = new MockFilterChain();

        assertEquals(200, run(request, chain).getStatus());

        assertNotNull(chain.getRequest());
        assertEquals(5L, request.getAttribute(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE));
        verify(credentials).requireActiveLogin(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void portalPathsAreProtectedToo() throws Exception {
        MockFilterChain denied = new MockFilterChain();
        assertEquals(401, run(request("GET", "/bff/v1/portal/home", null), denied).getStatus());
        assertNull(denied.getRequest());

        MockFilterChain allowed = new MockFilterChain();
        run(request("GET", "/bff/v1/portal/home", "Bearer " + customerToken(5)), allowed);
        assertNotNull(allowed.getRequest());
    }

    @Test
    void missingOrNonBearerAuthorizationIs401WithAChallengeAndNeverReachesTheChain() throws Exception {
        for (String header : new String[]{null, "", "Bearer", "Basic dXNlcjpwYXNz", customerToken(5)}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(request("GET", "/v1/api/accounts", header), chain);

            assertEquals(401, response.getStatus(), String.valueOf(header));
            assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
            assertTrue(response.getContentAsString().startsWith("Authentication required"));
            assertNull(chain.getRequest());
        }
        verifyNoInteractions(credentials);
    }

    @Test
    void garbageTamperedExpiredForeignAndNonNumericSubjectTokensAreAll401() throws Exception {
        String good = customerToken(5);
        String[] parts = good.split("\\.");
        SecretKey other = Keys.hmacShaKeyFor("a-completely-different-secret-of-32-chars!".getBytes(StandardCharsets.UTF_8));
        SecretKey ours = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder().issuer("bankingservices").subject("5").claim("type", "customer")
                .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(other, Jwts.SIG.HS256).compact();
        String expired = Jwts.builder().issuer("bankingservices").subject("5").claim("type", "customer")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200))).expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(ours, Jwts.SIG.HS256).compact();
        String nonNumeric = Jwts.builder().issuer("bankingservices").subject("not-a-number").claim("type", "customer")
                .issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(600))).signWith(ours, Jwts.SIG.HS256).compact();

        for (String bad : new String[]{"nope", parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "A", foreign, expired, nonNumeric}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(request("GET", "/v1/api/accounts", "Bearer " + bad), chain);

            assertEquals(401, response.getStatus(), bad);
            assertEquals("Invalid or expired token", response.getContentAsString());
            assertNull(chain.getRequest());
        }
    }

    @Test
    void anEmployeeTokenIsForbiddenOnCustomerEndpoints() throws Exception {
        String employeeToken = jwtService.issueEmployeeToken(Employee.builder().employeeNumber("EMP-000010").role(EmployeeRole.TELLER).build()).getToken();
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request("POST", "/v1/api/accounts/deposit", "Bearer " + employeeToken), chain);

        assertEquals(403, response.getStatus());
        assertEquals("This endpoint needs a customer token", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void aLoginThatIsNotActiveAnymoreIsForbiddenEvenWithAnUnexpiredToken() throws Exception {
        doThrow(new LoginNotActiveException("Customer login is SUSPENDED; only an ACTIVE login can perform transactions"))
                .when(credentials).requireActiveLogin(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any());
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request("GET", "/v1/api/accounts", "Bearer " + customerToken(5)), chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("SUSPENDED"));
        assertNull(chain.getRequest());
    }

    @Test
    void aTokenWhoseCustomerNoLongerHasALoginIs401() throws Exception {
        doThrow(new InvalidCredentialsException("Invalid username or password")).when(credentials).requireActiveLogin(any(), any());
        MockFilterChain chain = new MockFilterChain();

        assertEquals(401, run(request("GET", "/v1/api/accounts", "Bearer " + customerToken(5)), chain).getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void aTokenIssuedBeforeThePasswordChangedIs401() throws Exception {
        doThrow(new org.brite.banking.exception.InvalidTokenException("Invalid or expired token"))
                .when(credentials).requireActiveLogin(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any());
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request("GET", "/v1/api/accounts", "Bearer " + customerToken(5)), chain);

        assertEquals(401, response.getStatus());
        assertEquals("Invalid or expired token", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void onlyTheEndpointsAUserWithoutATokenMustReachAreOpen() throws Exception {
        for (String[] open : new String[][]{{"POST", "/v1/api/accounts/newaccount"},
                {"POST", "/v1/api/customers/login"}, {"POST", "/v1/api/customers/password-reset/questions"}, {"POST", "/v1/api/customers/password-reset"},
                {"POST", "/bff/v1/portal/login"}, {"GET", "/bff/v1/portal/security-questions/catalog"},
                {"POST", "/bff/v1/portal/password-reset/questions"}, {"POST", "/bff/v1/portal/password-reset"}}) {
            MockFilterChain chain = new MockFilterChain();
            assertEquals(200, run(request(open[0], open[1], null), chain).getStatus(), open[1]);
            assertNotNull(chain.getRequest(), open[1]);
        }
        for (String[] closed : new String[][]{{"GET", "/v1/api/accounts/newaccount"}, {"POST", "/v1/api/accounts/newaccount/"},
                {"POST", "/v1/api/accounts/newaccountx"}, {"POST", "/bff/v1/portal/accounts/open"}, {"POST", "/v1/api/accounts/lookup"},
                {"PUT", "/v1/api/customers/security-questions"}, {"GET", "/v1/api/customers/login"}, {"POST", "/v1/api/customers/password-reset/other"},
                {"PUT", "/bff/v1/portal/password"}, {"PUT", "/bff/v1/portal/security-questions"}, {"GET", "/bff/v1/portal/login"},
                {"POST", "/bff/v1/portal/security-questions/catalog"}, {"GET", "/bff/v1/portal/password-reset"}}) {
            MockFilterChain chain = new MockFilterChain();
            assertEquals(401, run(request(closed[0], closed[1], null), chain).getStatus(), closed[0] + " " + closed[1]);
            assertNull(chain.getRequest());
        }
    }

    @Test
    void corsPreflightsPassWithoutAToken() throws Exception {
        MockHttpServletRequest preflight = request("OPTIONS", "/bff/v1/portal/home", null);
        preflight.addHeader("Access-Control-Request-Method", "GET");
        MockFilterChain chain = new MockFilterChain();

        assertEquals(200, run(preflight, chain).getStatus());
        assertNotNull(chain.getRequest());
    }
}
