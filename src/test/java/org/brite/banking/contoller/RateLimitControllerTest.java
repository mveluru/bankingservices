package org.brite.banking.contoller;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.domain.CustomerRateLimitView;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.service.CustomerAccessService;
import org.brite.banking.service.CustomerQuotaService;
import org.brite.banking.service.StaffLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: a customer or employee reads only their own daily request usage, the identity coming from the token attribute. */
class RateLimitControllerTest {
    private CustomerQuotaService customerQuota;
    private StaffLoginService staffLogin;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        customerQuota = mock(CustomerQuotaService.class);
        staffLogin = mock(StaffLoginService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RateLimitController(customerQuota, staffLogin, new CustomerAccessService(mock(AccountRepository.class))))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void aCustomerReadsTheirOwnUsageForTheCustomerInTheToken() throws Exception {
        when(customerQuota.view(5L)).thenReturn(CustomerRateLimitView.builder().customerId(5L).dailyLimit(250).customLimit(250)
                .defaultLimit(1000).requestsToday(3).remainingToday(247).loginsToday(1).build());

        mockMvc.perform(get("/v1/api/customers/rate-limit").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(5)).andExpect(jsonPath("$.dailyLimit").value(250))
                .andExpect(jsonPath("$.remainingToday").value(247));
    }

    @Test
    void withoutAnAuthenticatedCustomerOrEmployeeBothCallsFailClosedWith401() throws Exception {
        mockMvc.perform(get("/v1/api/customers/rate-limit")).andExpect(status().isUnauthorized());
        verifyNoInteractions(customerQuota);

        when(staffLogin.ownRateLimit(null)).thenThrow(new org.brite.banking.exception.InvalidTokenException("Authentication required"));
        mockMvc.perform(get("/v1/api/staff/rate-limit")).andExpect(status().isUnauthorized());
    }

    @Test
    void anEmployeeReadsTheirOwnUsageAndAnInactiveOneIsRefused() throws Exception {
        when(staffLogin.ownRateLimit("EMP-T")).thenReturn(EmployeeRateLimitView.builder().employeeNumber("EMP-T").dailyLimit(1000)
                .defaultLimit(1000).requestsToday(9).remainingToday(991).loginsToday(2).build());
        when(staffLogin.ownRateLimit("EMP-X")).thenThrow(new EmployeeNotAuthorizedException("Employee EMP-X is ON_LEAVE and cannot perform this action"));

        mockMvc.perform(get("/v1/api/staff/rate-limit").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employeeNumber").value("EMP-T")).andExpect(jsonPath("$.loginsToday").value(2));
        mockMvc.perform(get("/v1/api/staff/rate-limit").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-X"))
                .andExpect(status().isForbidden());
    }
}
