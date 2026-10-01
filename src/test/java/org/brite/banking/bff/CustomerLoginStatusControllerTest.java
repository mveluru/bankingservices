package org.brite.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.CustomerLoginStatusController;
import org.brite.banking.bff.service.CustomerLoginStatusPortalService;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.CustomerNotFoundException;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: a customer's login status in the staff portal. */
class CustomerLoginStatusControllerTest {
    private static final String ATTR = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE;

    private CustomerLoginStatusPortalService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerLoginStatusPortalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerLoginStatusController(service))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void anAuthorisedEmployeeSetsAStatusAndGetsTheNewStatusBack() throws Exception {
        when(service.changeStatus(eq("EMP-M"), eq(11L), any())).thenReturn(
                LoginStatusView.builder().username("alice.smith").status(LoginStatus.SUSPENDED).statusReason("Under review").build());

        mockMvc.perform(put("/bff/v1/staff/customers/11/login-status").requestAttr(ATTR, "EMP-M").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Under review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Under review"));
    }

    @Test
    void privilegeAndLookupFailuresMapTo403And404() throws Exception {
        when(service.changeStatus(eq("EMP-T"), any(), any())).thenThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires MANAGE_CUSTOMER_LOGINS"));
        when(service.changeStatus(eq("EMP-M"), eq(99L), any())).thenThrow(new CustomerNotFoundException("Customer not found: 99"));

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
        verifyNoInteractions(service);
    }
}
