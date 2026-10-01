package org.brite.banking.contoller;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.service.CustomerAccessService;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.PasswordResetService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: binding, validation and status mapping of the password-reset endpoints. */
class PasswordControllerTest {
    private static final String ANSWERS = "[{\"question\":\"FIRST_CAR\",\"answer\":\"Honda Civic\"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"Oak Street\"},"
            + "{\"question\":\"FIRST_TEACHER\",\"answer\":\"Mrs Patel\"}]";

    private PasswordResetService service;
    private CustomerCredentialService customers;
    private EmployeeCredentialService employees;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PasswordResetService.class);
        customers = mock(CustomerCredentialService.class);
        employees = mock(EmployeeCredentialService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PasswordController(service, customers, employees, new CustomerAccessService(mock(AccountRepository.class))))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    private static String reset(String username, String newPassword) {
        return "{\"username\":\"" + username + "\",\"answers\":" + ANSWERS + ",\"newPassword\":\"" + newPassword + "\"}";
    }

    @Test
    void theQuestionsComeBackAsCodeAndTextForBothKindsOfUser() throws Exception {
        when(service.questionsFor(CredentialOwnerType.CUSTOMER, "alice.smith")).thenReturn(List.of(
                SecurityQuestionView.of(SecurityQuestion.FIRST_CAR), SecurityQuestionView.of(SecurityQuestion.FIRST_SCHOOL), SecurityQuestionView.of(SecurityQuestion.FIRST_TEACHER)));
        when(service.questionsFor(CredentialOwnerType.EMPLOYEE, "lucas.meyer")).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.BIRTH_CITY)));

        mockMvc.perform(post("/v1/api/customers/password-reset/questions").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice.smith\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].question").value("FIRST_CAR"))
                .andExpect(jsonPath("$[0].text").value("What was your first car?"));
        mockMvc.perform(post("/v1/api/staff/password-reset/questions").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"lucas.meyer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("BIRTH_CITY"));
    }

    @Test
    void aSuccessfulResetIs204WithNoBodyForBothKindsOfUser() throws Exception {
        mockMvc.perform(post("/v1/api/customers/password-reset").contentType(MediaType.APPLICATION_JSON).content(reset("alice.smith", "24681357")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mockMvc.perform(post("/v1/api/staff/password-reset").contentType(MediaType.APPLICATION_JSON).content(reset("lucas.meyer", "24681357")))
                .andExpect(status().isNoContent());

        verify(service).reset(eq(CredentialOwnerType.CUSTOMER), eq("alice.smith"), any(), eq("24681357"));
        verify(service).reset(eq(CredentialOwnerType.EMPLOYEE), eq("lucas.meyer"), any(), eq("24681357"));
    }

    @Test
    void failuresMapTo401LockedTo423AndAnInactiveLoginTo403WithPlainText() throws Exception {
        doThrow(new InvalidCredentialsException("Invalid username or answers")).when(service).reset(any(), eq("wrong.user"), any(), any());
        doThrow(new EmployeeLockedException("Too many wrong answers; password reset is locked until x")).when(service).reset(any(), eq("locked.user"), any(), any());
        doThrow(new LoginNotActiveException("Login is SUSPENDED; the password can't be reset until it is ACTIVE")).when(service).reset(any(), eq("sus.user"), any(), any());
        doThrow(new IllegalArgumentException("Password must be exactly 8 digits")).when(service).reset(any(), eq("bad.pw"), any(), any());

        mockMvc.perform(post("/v1/api/customers/password-reset").contentType(MediaType.APPLICATION_JSON).content(reset("wrong.user", "24681357")))
                .andExpect(status().isUnauthorized()).andExpect(content().string("Invalid username or answers"));
        mockMvc.perform(post("/v1/api/customers/password-reset").contentType(MediaType.APPLICATION_JSON).content(reset("locked.user", "24681357")))
                .andExpect(status().isLocked());
        mockMvc.perform(post("/v1/api/customers/password-reset").contentType(MediaType.APPLICATION_JSON).content(reset("sus.user", "24681357")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/customers/password-reset").contentType(MediaType.APPLICATION_JSON).content(reset("bad.pw", "1234")))
                .andExpect(status().isBadRequest()).andExpect(content().string("Password must be exactly 8 digits"));
    }

    @Test
    void aMalformedResetBodyIs400BeforeTheServiceRuns() throws Exception {
        String twoAnswers = "{\"username\":\"a.b\",\"answers\":[{\"question\":\"FIRST_CAR\",\"answer\":\"x1\"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"y1\"}],\"newPassword\":\"24681357\"}";
        String blankAnswer = "{\"username\":\"a.b\",\"answers\":[{\"question\":\"FIRST_CAR\",\"answer\":\" \"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"y1\"},{\"question\":\"FIRST_TEACHER\",\"answer\":\"z1\"}],\"newPassword\":\"24681357\"}";
        String unknownQuestion = "{\"username\":\"a.b\",\"answers\":[{\"question\":\"FAVORITE_COLOR\",\"answer\":\"x1\"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"y1\"},{\"question\":\"FIRST_TEACHER\",\"answer\":\"z1\"}],\"newPassword\":\"24681357\"}";
        for (String body : new String[]{twoAnswers, blankAnswer, unknownQuestion, "{\"username\":\"a.b\"}", "{\"answers\":" + ANSWERS + ",\"newPassword\":\"24681357\"}"}) {
            mockMvc.perform(post("/v1/api/customers/password-reset").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/v1/api/staff/password-reset/questions").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void aLoggedInCustomerSetsQuestionsUsingTheTokenIdentityNotAnythingInTheBody() throws Exception {
        when(service.setQuestions(eq(CredentialOwnerType.CUSTOMER), eq("5"), eq("20260005"), any())).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_CAR)));

        mockMvc.perform(put("/v1/api/customers/security-questions").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260005\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Honda"))));
        verify(service).setQuestions(eq(CredentialOwnerType.CUSTOMER), eq("5"), eq("20260005"), any());
    }

    @Test
    void withoutAnAuthenticatedCustomerSettingQuestionsFailsClosedWith401() throws Exception {
        mockMvc.perform(put("/v1/api/customers/security-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"20260005\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void aLoggedInEmployeeSetsQuestionsAndAWrongCurrentPasswordIs401() throws Exception {
        when(service.setQuestions(eq(CredentialOwnerType.EMPLOYEE), eq("EMP-000010"), eq("20260010"), any())).thenReturn(List.of());
        when(service.setQuestions(eq(CredentialOwnerType.EMPLOYEE), eq("EMP-000010"), eq("00000000"), any()))
                .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(put("/v1/api/staff/security-questions").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-000010")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260010\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/api/staff/security-questions").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-000010")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"00000000\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/api/staff/security-questions").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-000010")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aLoggedInCustomerChangesTheirPasswordUsingTheTokenIdentity() throws Exception {
        mockMvc.perform(put("/v1/api/customers/password").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260005\",\"newPassword\":\"13572468\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(customers).changePassword(5L, "20260005", "13572468");
    }

    @Test
    void aLoggedInEmployeeChangesTheirPasswordUsingTheTokenIdentity() throws Exception {
        mockMvc.perform(put("/v1/api/staff/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-000010")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260010\",\"newPassword\":\"13572468\"}"))
                .andExpect(status().isNoContent());
        verify(employees).changePassword("EMP-000010", "20260010", "13572468");
    }

    @Test
    void changePasswordFailuresMapTo401400423And403() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidCredentialsException("Invalid username or password")).when(customers).changePassword(5L, "00000000", "13572468");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("The new password must be different from the current password")).when(customers).changePassword(5L, "20260005", "20260005");
        org.mockito.Mockito.doThrow(new EmployeeLockedException("Too many failed login attempts; locked until x")).when(employees).changePassword(eq("EMP-L"), any(), any());
        org.mockito.Mockito.doThrow(new LoginNotActiveException("Employee EMP-S login is SUSPENDED; only an ACTIVE login can perform transactions")).when(employees).changePassword(eq("EMP-S"), any(), any());

        mockMvc.perform(put("/v1/api/customers/password").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"00000000\",\"newPassword\":\"13572468\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/api/customers/password").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260005\",\"newPassword\":\"20260005\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().string("The new password must be different from the current password"));
        mockMvc.perform(put("/v1/api/staff/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-L")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260010\",\"newPassword\":\"13572468\"}"))
                .andExpect(status().isLocked());
        mockMvc.perform(put("/v1/api/staff/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-S")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260010\",\"newPassword\":\"13572468\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changePasswordWithoutAnAuthenticatedUserOrWithABlankFieldIsRejectedBeforeTheService() throws Exception {
        String body = "{\"currentPassword\":\"20260005\",\"newPassword\":\"13572468\"}";
        mockMvc.perform(put("/v1/api/customers/password").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/api/staff/password").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/api/customers/password").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"20260005\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/v1/api/staff/password").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-000010")
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"13572468\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(customers, employees);
    }
}
