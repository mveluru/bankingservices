package org.brite.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.PortalAuthController;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.PortalAuthService;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
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

/** Standalone MockMvc: routing, binding, validation and status mapping of the portal sign-in and password calls. */
class PortalAuthControllerTest {
    private static final String ANSWERS = "[{\"question\":\"FIRST_CAR\",\"answer\":\"Honda Civic\"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"Oak Street\"},"
            + "{\"question\":\"FIRST_TEACHER\",\"answer\":\"Mrs Patel\"}]";

    private PortalAuthService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PortalAuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PortalAuthController(service, new CustomerAccessService(mock(AccountRepository.class))))
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
}
