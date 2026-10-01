package org.brite.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.CustomerPasswordController;
import org.brite.banking.bff.service.CustomerPasswordPortalService;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
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

import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: customer passwords in the BFF (customer portal calls and the staff portal's set-password). */
class CustomerPasswordControllerTest {
    private static final String ANSWERS = "[{\"question\":\"FIRST_CAR\",\"answer\":\"Honda Civic\"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"Oak Street\"},"
            + "{\"question\":\"FIRST_TEACHER\",\"answer\":\"Mrs Patel\"}]";

    private CustomerPasswordPortalService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CustomerPasswordPortalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerPasswordController(service, new CustomerAccessService(mock(AccountRepository.class))))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void changePasswordAndSetQuestionsUseTheCustomerFromTheToken() throws Exception {
        when(service.setSecurityQuestions(eq(5L), any())).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_CAR)));

        mockMvc.perform(put("/bff/v1/portal/password").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260005\",\"newPassword\":\"13572468\"}"))
                .andExpect(status().isNoContent());
        verify(service).changePassword(eq(5L), any());

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
        verifyNoInteractions(service);
    }

    @Test
    void theCatalogAndTheTwoResetCallsAreRoutedAndValidated() throws Exception {
        when(service.questionCatalog()).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_CAR), SecurityQuestionView.of(SecurityQuestion.BIRTH_CITY)));
        when(service.resetQuestions("alice.smith")).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_SCHOOL)));

        mockMvc.perform(get("/bff/v1/portal/security-questions/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post("/bff/v1/portal/password-reset/questions").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice.smith\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].question").value("FIRST_SCHOOL"));
        mockMvc.perform(post("/bff/v1/portal/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice.smith\",\"newPassword\":\"13572468\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isNoContent());
        verify(service).resetPassword(any());

        mockMvc.perform(post("/bff/v1/portal/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice.smith\",\"newPassword\":\"13572468\",\"answers\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aManagerSetsACustomerPasswordInTheStaffPortalWith204AndATellerGets403() throws Exception {
        doThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires MANAGE_CUSTOMER_LOGINS"))
                .when(service).setPassword(eq("EMP-T"), eq(11L), any());

        mockMvc.perform(put("/bff/v1/staff/customers/11/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"24681357\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).setPassword(eq("EMP-M"), eq(11L), any());
        mockMvc.perform(put("/bff/v1/staff/customers/11/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"24681357\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/bff/v1/staff/customers/11/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
}
