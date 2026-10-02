package org.brite.banking.bff;

import org.brite.banking.domain.CustomerRateLimitView;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import org.brite.banking.bff.controller.CustomerPortalAuthController;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.CustomerPortalAuthService;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.CustomerNotFoundException;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.service.CustomerAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc: the BFF's customer credential calls in one controller - sign-in, staff create-login and set-status, customer change password /
 * security questions / catalog / reset, and staff set-password.
 */
class CustomerPortalAuthControllerTest {
    private static final String ATTR = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE;
    private static final String ANSWERS = "[{\"question\":\"FIRST_CAR\",\"answer\":\"Honda Civic\"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"Oak Street\"},"
            + "{\"question\":\"FIRST_TEACHER\",\"answer\":\"Mrs Patel\"}]";

    private CustomerPortalAuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(CustomerPortalAuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerPortalAuthController(authService,
                        new CustomerAccessService(mock(AccountRepository.class))))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void loginReturnsTheTokenCustomerAndHomeWithNoStore() throws Exception {
        when(authService.login("customer0005", "20260005", "TX")).thenReturn(new PortalLoginResponse("signed.jwt", "Bearer", 1800,
                AuthenticatedCustomer.builder().customerId(5L).firstName("Alice").lastName("Smith").build(), new PortalHomeResponse(2, 1, List.of(), List.of())));

        mockMvc.perform(post("/bff/v1/portal/login").param("state", "TX").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer0005\",\"password\":\"20260005\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("signed.jwt"))
                .andExpect(jsonPath("$.customer.customerId").value(5))
                .andExpect(jsonPath("$.home.totalActiveAccounts").value(2))
                .andExpect(content().string(not(containsString("20260005"))));
    }

    @Test
    void loginFailuresMapTo401423And403AndAMissingFieldIs400() throws Exception {
        when(authService.login(eq("bad"), any(), any())).thenThrow(new InvalidCredentialsException("Invalid username or password"));
        when(authService.login(eq("locked"), any(), any())).thenThrow(new EmployeeLockedException("Too many failed login attempts; locked until x"));
        when(authService.login(eq("sus"), any(), any())).thenThrow(new LoginNotActiveException("Customer login is SUSPENDED; only an ACTIVE login can perform transactions"));

        for (String[] c : new String[][]{{"bad", "401"}, {"locked", "423"}, {"sus", "403"}}) {
            mockMvc.perform(post("/bff/v1/portal/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + c[0] + "\",\"password\":\"20260005\"}"))
                    .andExpect(status().is(Integer.parseInt(c[1])));
        }
        mockMvc.perform(post("/bff/v1/portal/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"x\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void creatingACustomerLoginIs201UsesTheEmployeeFromTheAttributeAndNeverEchoesThePassword() throws Exception {
        when(authService.createLogin(eq("EMP-M"), eq(11L), any())).thenReturn(LoginStatusView.builder().username("alice.smith").status(LoginStatus.ACTIVE).build());
        when(authService.createLogin(eq("EMP-T"), eq(11L), any())).thenThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires MANAGE_CUSTOMER_LOGINS"));
        when(authService.createLogin(eq("EMP-M"), eq(99L), any())).thenThrow(new IllegalArgumentException("Customer 99 already has a login"));

        mockMvc.perform(post("/bff/v1/staff/customers/11/login").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice.smith\",\"password\":\"13572468\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice.smith"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(content().string(not(containsString("13572468"))));
        mockMvc.perform(post("/bff/v1/staff/customers/11/login").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice.smith\",\"password\":\"13572468\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/staff/customers/99/login").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice.smith\",\"password\":\"13572468\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/bff/v1/staff/customers/11/login").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice.smith\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAuthorisedEmployeeSetsAStatusAndGetsTheNewStatusBack() throws Exception {
        when(authService.changeStatus(eq("EMP-M"), eq(11L), any())).thenReturn(
                LoginStatusView.builder().username("alice.smith").status(LoginStatus.SUSPENDED).statusReason("Under review").build());

        mockMvc.perform(put("/bff/v1/staff/customers/11/login-status").requestAttr(ATTR, "EMP-M").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Under review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Under review"));
    }

    @Test
    void privilegeAndLookupFailuresMapTo403And404() throws Exception {
        when(authService.changeStatus(eq("EMP-T"), any(), any())).thenThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires MANAGE_CUSTOMER_LOGINS"));
        when(authService.changeStatus(eq("EMP-M"), eq(99L), any())).thenThrow(new CustomerNotFoundException("Customer not found: 99"));

        mockMvc.perform(put("/bff/v1/staff/customers/11/login-status").requestAttr(ATTR, "EMP-T").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/bff/v1/staff/customers/99/login-status").requestAttr(ATTR, "EMP-M").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isNotFound());
    }

    @Test
    void aMissingStatusOrAnOverLongReasonIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(put("/bff/v1/staff/customers/11/login-status").requestAttr(ATTR, "EMP-M").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/bff/v1/staff/customers/11/login-status").requestAttr(ATTR, "EMP-M").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\",\"reason\":\"" + "x".repeat(201) + "\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void changePasswordAndSetQuestionsUseTheCustomerFromTheToken() throws Exception {
        when(authService.setSecurityQuestions(eq(5L), any())).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_CAR)));

        mockMvc.perform(put("/bff/v1/portal/password").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260005\",\"newPassword\":\"13572468\"}"))
                .andExpect(status().isNoContent());
        verify(authService).changePassword(eq(5L), any());

        mockMvc.perform(put("/bff/v1/portal/security-questions").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260005\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("FIRST_CAR"))
                .andExpect(content().string(not(containsString("Honda"))));
    }

    @Test
    void withoutAnAuthenticatedCustomerThePasswordCallsFailClosedWith401() throws Exception {
        mockMvc.perform(put("/bff/v1/portal/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"20260005\",\"newPassword\":\"13572468\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/bff/v1/portal/security-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"20260005\",\"answers\":" + ANSWERS + "}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    void theCatalogAndTheTwoResetCallsAreRoutedAndValidated() throws Exception {
        when(authService.questionCatalog()).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_CAR), SecurityQuestionView.of(SecurityQuestion.BIRTH_CITY)));
        when(authService.resetQuestions("alice.smith")).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_SCHOOL)));

        mockMvc.perform(get("/bff/v1/portal/security-questions/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post("/bff/v1/portal/password-reset/questions").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice.smith\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].question").value("FIRST_SCHOOL"));
        mockMvc.perform(post("/bff/v1/portal/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice.smith\",\"newPassword\":\"13572468\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isNoContent());
        verify(authService).resetPassword(any());

        mockMvc.perform(post("/bff/v1/portal/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice.smith\",\"newPassword\":\"13572468\",\"answers\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aManagerSetsACustomerPasswordInTheStaffPortalWith204AndATellerGets403() throws Exception {
        doThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires MANAGE_CUSTOMER_LOGINS"))
                .when(authService).setPassword(eq("EMP-T"), eq(11L), any());

        mockMvc.perform(put("/bff/v1/staff/customers/11/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"24681357\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(authService).setPassword(eq("EMP-M"), eq(11L), any());
        mockMvc.perform(put("/bff/v1/staff/customers/11/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"24681357\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/bff/v1/staff/customers/11/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void myRateLimitReturnsTheTokenCustomersOwnUsageWithNoStore() throws Exception {
        when(authService.myRateLimit(5L)).thenReturn(CustomerRateLimitView.builder().customerId(5L).dailyLimit(1000).defaultLimit(1000)
                .requestsToday(12).remainingToday(988).loginsToday(3).build());

        mockMvc.perform(get("/bff/v1/portal/rate-limit").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.customerId").value(5))
                .andExpect(jsonPath("$.loginsToday").value(3))
                .andExpect(jsonPath("$.remainingToday").value(988));
    }

    @Test
    void myRateLimitWithoutAnAuthenticatedCustomerFailsClosedWith401() throws Exception {
        mockMvc.perform(get("/bff/v1/portal/rate-limit")).andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }
}
