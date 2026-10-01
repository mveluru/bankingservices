package org.brite.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.CustomerLoginController;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.CustomerLoginPortalService;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: the customer sign-in (customer portal) and customer login creation (staff portal). */
class CustomerLoginControllerTest {
    private CustomerLoginPortalService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerLoginPortalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerLoginController(service))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void loginReturnsTheTokenCustomerAndHomeWithNoStore() throws Exception {
        when(service.login("customer0005", "20260005", "TX")).thenReturn(new PortalLoginResponse("signed.jwt", "Bearer", 1800,
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
        when(service.login(eq("bad"), any(), any())).thenThrow(new InvalidCredentialsException("Invalid username or password"));
        when(service.login(eq("locked"), any(), any())).thenThrow(new EmployeeLockedException("Too many failed login attempts; locked until x"));
        when(service.login(eq("sus"), any(), any())).thenThrow(new LoginNotActiveException("Customer login is SUSPENDED; only an ACTIVE login can perform transactions"));

        for (String[] c : new String[][]{{"bad", "401"}, {"locked", "423"}, {"sus", "403"}}) {
            mockMvc.perform(post("/bff/v1/portal/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + c[0] + "\",\"password\":\"20260005\"}"))
                    .andExpect(status().is(Integer.parseInt(c[1])));
        }
        mockMvc.perform(post("/bff/v1/portal/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"x\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void creatingACustomerLoginIs201UsesTheEmployeeFromTheAttributeAndNeverEchoesThePassword() throws Exception {
        when(service.createLogin(eq("EMP-M"), eq(11L), any())).thenReturn(LoginStatusView.builder().username("alice.smith").status(LoginStatus.ACTIVE).build());
        when(service.createLogin(eq("EMP-T"), eq(11L), any())).thenThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires MANAGE_CUSTOMER_LOGINS"));
        when(service.createLogin(eq("EMP-M"), eq(99L), any())).thenThrow(new IllegalArgumentException("Customer 99 already has a login"));

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
}
