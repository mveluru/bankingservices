package org.brite.banking.bff;

import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.CustomerPortalAuthService;
import org.brite.banking.bff.service.PortalOrchestrationService;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SecurityAnswerRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.LoginService;
import org.brite.banking.service.PasswordResetService;
import org.brite.banking.service.StaffLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test: the customer-auth BFF service only composes / delegates to the banking services that own the rules. */
class CustomerPortalAuthServiceTest {
    private LoginService loginService;
    private PortalOrchestrationService portalService;
    private StaffLoginService staffLoginService;
    private CustomerCredentialService customers;
    private PasswordResetService resets;
    private CustomerPortalAuthService service;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        portalService = mock(PortalOrchestrationService.class);
        staffLoginService = mock(StaffLoginService.class);
        customers = mock(CustomerCredentialService.class);
        resets = mock(PasswordResetService.class);
        service = new CustomerPortalAuthService(loginService, portalService, staffLoginService, customers, resets);
    }

    @Test
    void loginReturnsTheTokenTheCustomerAndTheirHomeScreenInOneResponse() {
        AuthenticatedCustomer customer = AuthenticatedCustomer.builder().customerId(5L).firstName("Alice").lastName("Smith").build();
        when(loginService.customerLogin("customer0005", "20260005")).thenReturn(
                CustomerLoginResponse.builder().accessToken("signed.jwt.value").expiresIn(1800).customer(customer).build());
        PortalHomeResponse home = new PortalHomeResponse(1, 0, List.of(), List.of());
        when(portalService.home("TX", 5L)).thenReturn(home);

        PortalLoginResponse response = service.login("customer0005", "20260005", "TX");

        assertEquals("signed.jwt.value", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(customer, response.customer());
        assertSame(home, response.home());
        assertFalse(response.toString().contains("signed.jwt.value"), "the token must not appear in toString");
    }

    @Test
    void aFailedLoginPropagatesAndNeverBuildsTheHomeScreen() {
        when(loginService.customerLogin("customer0005", "00000000")).thenThrow(new InvalidCredentialsException("Invalid username or password"));

        assertThrows(InvalidCredentialsException.class, () -> service.login("customer0005", "00000000", null));
        verifyNoInteractions(portalService);
    }

    @Test
    void creatingACustomerLoginDelegatesToTheBankingStaffServiceWhichChecksThePrivilege() {
        LoginRequest request = new LoginRequest("alice.smith", "13572468");
        when(staffLoginService.createCustomerLogin("EMP-M", 11L, request))
                .thenReturn(LoginStatusView.builder().username("alice.smith").status(LoginStatus.ACTIVE).build());
        when(staffLoginService.createCustomerLogin("EMP-T", 11L, request)).thenThrow(new EmployeeNotAuthorizedException("not authorized"));

        assertEquals("alice.smith", service.createLogin("EMP-M", 11L, request).getUsername());
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.createLogin("EMP-T", 11L, request));
        verifyNoInteractions(loginService);
    }

    @Test
    void changePasswordAndSecurityQuestionsDelegateWithTheCustomerIdFromTheToken() {
        service.changePassword(5L, new ChangePasswordRequest("20260005", "13572468"));
        verify(customers).changePassword(5L, "20260005", "13572468");

        List<SecurityAnswerRequest> answers = List.of(new SecurityAnswerRequest(SecurityQuestion.FIRST_CAR, "a1"),
                new SecurityAnswerRequest(SecurityQuestion.FIRST_SCHOOL, "b1"), new SecurityAnswerRequest(SecurityQuestion.FIRST_TEACHER, "c1"));
        service.setSecurityQuestions(5L, new SetSecurityQuestionsRequest("20260005", answers));
        verify(resets).setQuestions(CredentialOwnerType.CUSTOMER, "5", "20260005", answers);
    }

    @Test
    void passwordResetUsesTheCustomerFlowAndTheCatalogListsEveryQuestion() {
        List<SecurityAnswerRequest> answers = List.of(new SecurityAnswerRequest(SecurityQuestion.FIRST_CAR, "a1"));
        service.resetPassword(new PasswordResetRequest("alice.smith", answers, "13572468"));
        verify(resets).reset(CredentialOwnerType.CUSTOMER, "alice.smith", answers, "13572468");
        service.resetQuestions("alice.smith");
        verify(resets).questionsFor(CredentialOwnerType.CUSTOMER, "alice.smith");

        var catalog = service.questionCatalog();
        assertEquals(SecurityQuestion.values().length, catalog.size());
        assertEquals("What was your first car?", catalog.get(0).getText());
        verifyNoInteractions(staffLoginService);
    }

    @Test
    void aManagerSettingACustomerPasswordGoesThroughTheBankingStaffService() {
        AdminSetPasswordRequest request = new AdminSetPasswordRequest("24681357");
        service.setPassword("EMP-M", 11L, request);
        verify(staffLoginService).setCustomerPassword("EMP-M", 11L, request);
    }

    @Test
    void changingACustomersLoginStatusDelegatesToTheBankingStaffServiceWhichChecksThePrivilege() {
        ChangeLoginStatusRequest request = new ChangeLoginStatusRequest(LoginStatus.SUSPENDED, "claude test");
        when(staffLoginService.changeCustomerLoginStatus("EMP-M", 11L, request))
                .thenReturn(LoginStatusView.builder().username("alice.smith").status(LoginStatus.SUSPENDED).build());
        when(staffLoginService.changeCustomerLoginStatus("EMP-T", 11L, request)).thenThrow(new EmployeeNotAuthorizedException("not authorized"));

        assertEquals(LoginStatus.SUSPENDED, service.changeStatus("EMP-M", 11L, request).getStatus());
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.changeStatus("EMP-T", 11L, request));
        verifyNoInteractions(loginService);
    }
}
