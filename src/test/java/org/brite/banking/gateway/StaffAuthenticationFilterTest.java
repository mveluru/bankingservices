package org.brite.banking.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.brite.banking.contoller.StaffController;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.rules.JwtProperties;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.JwtService;
import org.brite.banking.service.StaffAccountService;
import org.brite.banking.service.StaffLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Plain servlet-object tests of the filter, plus one MockMvc run with the real filter in front of the real staff controller. */
class StaffAuthenticationFilterTest {
    private static final String SECRET = "filter-test-secret-that-is-at-least-32-chars";

    private JwtService jwtService;
    private StaffAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        jwtService = new JwtService(properties);
        filter = new StaffAuthenticationFilter(jwtService);
    }

    private String employeeToken(String number) {
        return jwtService.issueEmployeeToken(Employee.builder().employeeNumber(number).role(EmployeeRole.TELLER).build()).getToken();
    }

    private MockHttpServletRequest request(String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/brite" + path);
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
    void aValidEmployeeTokenPassesAndTheSubjectBecomesTheActingEmployee() throws Exception {
        MockHttpServletRequest request = request("/v1/api/staff/employees", "Bearer " + employeeToken("EMP-000010"));
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "the request must reach the rest of the chain");
        assertEquals("EMP-000010", request.getAttribute(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE));
    }

    @Test
    void theSchemeIsCaseInsensitive() throws Exception {
        MockHttpServletRequest request = request("/v1/api/staff/employees", "bearer " + employeeToken("EMP-000010"));
        MockFilterChain chain = new MockFilterChain();
        run(request, chain);
        assertNotNull(chain.getRequest());
    }

    @Test
    void missingOrNonBearerAuthorizationIs401WithAChallengeAndNeverReachesTheChain() throws Exception {
        for (String header : new String[]{null, "", "Bearer", "Bearer   ", "Basic dXNlcjpwYXNz", "Token abc", employeeToken("EMP-000010")}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(request("/v1/api/staff/employees", header), chain);

            assertEquals(401, response.getStatus(), String.valueOf(header));
            assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
            assertTrue(response.getContentAsString().startsWith("Authentication required"));
            assertNull(chain.getRequest(), String.valueOf(header));
        }
    }

    @Test
    void garbageTamperedExpiredAndForeignTokensAreAll401WithoutEchoingTheToken() throws Exception {
        String good = employeeToken("EMP-000010");
        String[] parts = good.split("\\.");
        SecretKey other = Keys.hmacShaKeyFor("a-completely-different-secret-of-32-chars!".getBytes(StandardCharsets.UTF_8));
        SecretKey ours = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder().issuer("bankingservices").subject("EMP-000001").claim("type", "employee")
                .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(other, Jwts.SIG.HS256).compact();
        String expired = Jwts.builder().issuer("bankingservices").subject("EMP-000001").claim("type", "employee")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200))).expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(ours, Jwts.SIG.HS256).compact();

        for (String bad : new String[]{"not-a-jwt", parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "A", foreign, expired}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(request("/v1/api/staff/employees", "Bearer " + bad), chain);

            assertEquals(401, response.getStatus(), bad);
            assertEquals("Invalid or expired token", response.getContentAsString());
            assertNull(chain.getRequest());
        }
    }

    @Test
    void aCustomerTokenIsForbiddenOnStaffEndpoints() throws Exception {
        String customerToken = jwtService.issueCustomerToken(AuthenticatedCustomer.builder().customerId(5L).build()).getToken();
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request("/v1/api/staff/accounts/CH-1/close", "Bearer " + customerToken), chain);

        assertEquals(403, response.getStatus());
        assertEquals("This endpoint needs an employee token", response.getContentAsString());
        assertNull(chain.getRequest());
    }

    @Test
    void theLoginEndpointIsTheOnlyStaffPathThatNeedsNoToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        assertEquals(200, run(request("/v1/api/staff/login", null), chain).getStatus());
        assertNotNull(chain.getRequest());

        for (String path : new String[]{"/v1/api/staff/login/", "/v1/api/staff/loginx", "/v1/api/staff/employees/login", "/v1/api/staff/accounts/deposit"}) {
            MockFilterChain other = new MockFilterChain();
            assertEquals(401, run(request(path, null), other).getStatus(), path);
            assertNull(other.getRequest(), path);
        }
    }

    @Test
    void aClientSuppliedAttributeOrLegacyHeaderCannotImpersonateAnEmployee() throws Exception {
        MockHttpServletRequest request = request("/v1/api/staff/employees", null);
        request.addHeader("X-Employee-Number", "EMP-000001");
        MockFilterChain chain = new MockFilterChain();

        assertEquals(401, run(request, chain).getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void withTheRealStaffControllerBehindItTheTokenSubjectIsTheActingEmployee() throws Exception {
        EmployeeService employeeService = mock(EmployeeService.class);
        when(employeeService.getEmployee("EMP-000010", "EMP-000010")).thenReturn(
                Employee.builder().employeeNumber("EMP-000010").firstName("Lucas").role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).build());
        StaffController controller = new StaffController(mock(StaffAccountService.class), employeeService, mock(StaffLoginService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter())
                .build();

        mockMvc.perform(get("/v1/api/staff/employees/EMP-000010").header("Authorization", "Bearer " + employeeToken("EMP-000010")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lucas"));
        mockMvc.perform(get("/v1/api/staff/employees/EMP-000010"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mockMvc.perform(post("/v1/api/staff/accounts/CH-1/close").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid or expired token"));
    }
}
