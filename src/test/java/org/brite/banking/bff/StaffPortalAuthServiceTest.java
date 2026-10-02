package org.brite.banking.bff;

import org.brite.banking.service.StaffLoginService;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.bff.dto.PortalLocation;
import org.brite.banking.bff.dto.StaffPortalLoginResponse;
import org.brite.banking.bff.service.PortalOrchestrationService;
import org.brite.banking.bff.service.StaffPortalAuthService;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.LocationType;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.StaffLoginResponse;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SecurityAnswerRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.LoginService;
import org.brite.banking.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffPortalAuthServiceTest {
    private LoginService loginService;
    private PortalOrchestrationService portal;
    private EmployeeCredentialService employees;
    private PasswordResetService resets;
    private StaffLoginService staffLogins;
    private StaffPortalAuthService service;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        portal = mock(PortalOrchestrationService.class);
        employees = mock(EmployeeCredentialService.class);
        resets = mock(PasswordResetService.class);
        staffLogins = mock(StaffLoginService.class);
        service = new StaffPortalAuthService(loginService, portal, employees, resets, staffLogins);
    }

    private static Employee lucas(Long branch) {
        return Employee.builder().employeeNumber("EMP-000010").firstName("Lucas").lastName("Meyer").email("lucas.meyer@brite-bank.example")
                .role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).bankLocationId(branch).build();
    }

    @Test
    void loginReturnsTheTokenTheEmployeeCardWithPrivilegesAndTheirBranch() {
        when(loginService.staffLogin("lucas.meyer", "20260010")).thenReturn(
                StaffLoginResponse.builder().accessToken("signed.jwt.value").expiresIn(1800).employee(lucas(1L)).build());
        PortalLocation branch = new PortalLocation(1L, "Austin Downtown Branch", LocationType.OFFICE, null, "Austin", "TX", null, null, null, null, null, null);
        when(portal.location(1L)).thenReturn(branch);

        StaffPortalLoginResponse response = service.login("lucas.meyer", "20260010");

        assertEquals("signed.jwt.value", response.accessToken());
        assertEquals("EMP-000010", response.employee().employeeNumber());
        assertEquals(4, response.employee().privileges().size());
        assertSame(branch, response.branch());
        assertFalse(response.toString().contains("signed.jwt.value"), "the token must not appear in toString");
        assertFalse(response.toString().contains("brite-bank.example"));
    }

    @Test
    void anAreaManagerWithoutABranchGetsANullBranchAndAFailedLoginBuildsNothing() {
        when(loginService.staffLogin("priya.raman", "20260001")).thenReturn(
                StaffLoginResponse.builder().accessToken("t").employee(lucas(null)).build());
        when(portal.location(null)).thenReturn(null);
        assertNull(service.login("priya.raman", "20260001").branch());

        when(loginService.staffLogin("lucas.meyer", "00000000")).thenThrow(new InvalidCredentialsException("Invalid username or password"));
        assertThrows(InvalidCredentialsException.class, () -> service.login("lucas.meyer", "00000000"));
        verify(portal, org.mockito.Mockito.times(1)).location(null);
    }

    @Test
    void passwordAndQuestionCallsDelegateWithTheEmployeeNumberFromTheToken() {
        service.changePassword("EMP-000010", new ChangePasswordRequest("20260010", "13572468"));
        verify(employees).changePassword("EMP-000010", "20260010", "13572468");

        List<SecurityAnswerRequest> answers = List.of(new SecurityAnswerRequest(SecurityQuestion.FIRST_CAR, "a1"),
                new SecurityAnswerRequest(SecurityQuestion.FIRST_SCHOOL, "b1"), new SecurityAnswerRequest(SecurityQuestion.FIRST_TEACHER, "c1"));
        service.setSecurityQuestions("EMP-000010", new SetSecurityQuestionsRequest("20260010", answers));
        verify(resets).setQuestions(CredentialOwnerType.EMPLOYEE, "EMP-000010", "20260010", answers);
    }

    @Test
    void resetCallsUseTheEmployeeFlowAndTheCatalogIsTheSharedOne() {
        List<SecurityAnswerRequest> answers = List.of(new SecurityAnswerRequest(SecurityQuestion.FIRST_CAR, "a1"));
        service.resetPassword(new PasswordResetRequest("lucas.meyer", answers, "13572468"));
        verify(resets).reset(CredentialOwnerType.EMPLOYEE, "lucas.meyer", answers, "13572468");
        service.resetQuestions("lucas.meyer");
        verify(resets).questionsFor(CredentialOwnerType.EMPLOYEE, "lucas.meyer");
        assertEquals(SecurityQuestion.values().length, service.questionCatalog().size());
        verifyNoInteractions(loginService);
    }

    @Test
    void myRateLimitIsTheCallersOwnUsageFromTheBankingStaffServiceWhichRequiresAnActiveEmployee() {
        EmployeeRateLimitView view = EmployeeRateLimitView.builder().employeeNumber("EMP-000010").dailyLimit(1000).defaultLimit(1000)
                .requestsToday(4).remainingToday(996).loginsToday(2).build();
        when(staffLogins.ownRateLimit("EMP-000010")).thenReturn(view);
        when(staffLogins.ownRateLimit("EMP-000016")).thenThrow(new org.brite.banking.exception.EmployeeNotAuthorizedException("Employee EMP-000016 is ON_LEAVE"));

        assertSame(view, service.myRateLimit("EMP-000010"));
        org.junit.jupiter.api.Assertions.assertThrows(org.brite.banking.exception.EmployeeNotAuthorizedException.class, () -> service.myRateLimit("EMP-000016"));
    }
}
