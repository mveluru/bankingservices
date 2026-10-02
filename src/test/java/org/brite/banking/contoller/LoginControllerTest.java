package org.brite.banking.contoller;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.CustomerQuotaService;
import org.brite.banking.service.EmployeeQuotaService;
import org.brite.banking.rules.JwtProperties;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.JwtService;
import org.brite.banking.service.LoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc (no Spring context, no MySQL): login binding, validation and status-code mapping. */
class LoginControllerTest {
    private EmployeeCredentialService employeeService;
    private CustomerCredentialService customerService;
    private JwtService jwtService;
    private CustomerQuotaService quotaService;
    private EmployeeQuotaService employeeQuotaService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        employeeService = mock(EmployeeCredentialService.class);
        customerService = mock(CustomerCredentialService.class);
        JwtProperties properties = new JwtProperties();
        properties.setSecret("controller-test-secret-at-least-32-chars!!");
        jwtService = new JwtService(properties);
        quotaService = mock(CustomerQuotaService.class);
        employeeQuotaService = mock(EmployeeQuotaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(new LoginService(employeeService, customerService, jwtService, quotaService, employeeQuotaService)))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    private static String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void staffLoginReturnsTheEmployeeWithPrivilegesAndNoPassword() throws Exception {
        when(employeeService.verify("lucas.meyer", "20260010")).thenReturn(Employee.builder()
                .employeeNumber("EMP-000010").firstName("Lucas").lastName("Meyer")
                .role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).build());

        mockMvc.perform(post("/v1/api/staff/login").contentType(MediaType.APPLICATION_JSON).content(body("lucas.meyer", "20260010")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.employee.employeeNumber").value("EMP-000010"))
                .andExpect(jsonPath("$.employee.privileges.length()").value(4))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("20260010"))));
        verify(employeeQuotaService).recordLogin("EMP-000010");
    }

    @Test
    void customerLoginReturnsOnlyIdAndName() throws Exception {
        when(customerService.verify("customer0005", "20260005")).thenReturn(
                AuthenticatedCustomer.builder().customerId(5L).firstName("Alice").lastName("Smith").build());

        mockMvc.perform(post("/v1/api/customers/login").contentType(MediaType.APPLICATION_JSON).content(body("customer0005", "20260005")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.customer.customerId").value(5))
                .andExpect(jsonPath("$.customer.firstName").value("Alice"))
                .andExpect(jsonPath("$.customer.length()").value(3));
        verify(quotaService).recordLogin(5L);
    }

    @Test
    void wrongCredentialsAre401WithTheGenericMessage() throws Exception {
        when(employeeService.verify("lucas.meyer", "00000000")).thenThrow(new InvalidCredentialsException("Invalid username or password"));
        when(customerService.verify("nobody", "00000000")).thenThrow(new InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/v1/api/staff/login").contentType(MediaType.APPLICATION_JSON).content(body("lucas.meyer", "00000000")))
                .andExpect(status().isUnauthorized()).andExpect(content().string("Invalid username or password"));
        mockMvc.perform(post("/v1/api/customers/login").contentType(MediaType.APPLICATION_JSON).content(body("nobody", "00000000")))
                .andExpect(status().isUnauthorized()).andExpect(content().string("Invalid username or password"));
    }

    @Test
    void lockedIs423AndANonActiveLoginIs403() throws Exception {
        when(employeeService.verify("locked.user", "20260010")).thenThrow(new EmployeeLockedException("Too many failed login attempts; locked until x"));
        when(customerService.verify("customer0006", "20260006")).thenThrow(new LoginNotActiveException("Customer login is SUSPENDED; only an ACTIVE login can perform transactions"));

        mockMvc.perform(post("/v1/api/staff/login").contentType(MediaType.APPLICATION_JSON).content(body("locked.user", "20260010")))
                .andExpect(status().isLocked());
        mockMvc.perform(post("/v1/api/customers/login").contentType(MediaType.APPLICATION_JSON).content(body("customer0006", "20260006")))
                .andExpect(status().isForbidden());
        // only a successful login is counted for the day
        verifyNoInteractions(quotaService);
        verifyNoInteractions(employeeQuotaService);
    }

    @Test
    void missingUsernameOrPasswordIs400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/v1/api/staff/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"lucas.meyer\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/api/customers/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"20260005\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/api/staff/login").contentType(MediaType.APPLICATION_JSON).content(body(" ", "20260010")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(employeeService, customerService);
    }

    @Test
    void theReturnedTokensVerifyAndIdentifyTheRightPrincipal() throws Exception {
        when(employeeService.verify("lucas.meyer", "20260010")).thenReturn(Employee.builder()
                .employeeNumber("EMP-000010").role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).build());
        when(customerService.verify("customer0005", "20260005")).thenReturn(
                AuthenticatedCustomer.builder().customerId(5L).firstName("Alice").lastName("Smith").build());

        String staffToken = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(
                mockMvc.perform(post("/v1/api/staff/login").contentType(MediaType.APPLICATION_JSON).content(body("lucas.meyer", "20260010")))
                        .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        String customerToken = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(
                mockMvc.perform(post("/v1/api/customers/login").contentType(MediaType.APPLICATION_JSON).content(body("customer0005", "20260005")))
                        .andReturn().getResponse().getContentAsString()).get("accessToken").asText();

        var staff = jwtService.parse(staffToken);
        org.junit.jupiter.api.Assertions.assertEquals("employee", staff.getType());
        org.junit.jupiter.api.Assertions.assertEquals("EMP-000010", staff.getSubject());
        org.junit.jupiter.api.Assertions.assertEquals(EmployeeRole.TELLER, staff.getRole());
        var customer = jwtService.parse(customerToken);
        org.junit.jupiter.api.Assertions.assertEquals("customer", customer.getType());
        org.junit.jupiter.api.Assertions.assertEquals("5", customer.getSubject());
    }

    @Test
    void aFailedLoginNeverReturnsAToken() throws Exception {
        when(employeeService.verify("lucas.meyer", "00000000")).thenThrow(new InvalidCredentialsException("Invalid username or password"));
        mockMvc.perform(post("/v1/api/staff/login").contentType(MediaType.APPLICATION_JSON).content(body("lucas.meyer", "00000000")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("eyJ"))));
    }
}
