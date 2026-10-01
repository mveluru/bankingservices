package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.PortalEmployee;
import org.brite.banking.bff.dto.StaffPortalLoginResponse;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.domain.StaffLoginResponse;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.LoginService;
import org.brite.banking.service.PasswordResetService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The staff portal's account-access calls (login, change password, security questions, forgotten-password reset), composed from the
 * banking services in process. No rules of its own: credential checks, lockout, status and token issuing stay in {@link LoginService},
 * {@link EmployeeCredentialService} and {@link PasswordResetService}; a failed login never builds the rest of the response.
 */
@Service
@RequiredArgsConstructor
public class StaffPortalAuthService {
    private final LoginService loginService;
    private final PortalOrchestrationService portalService;
    private final PortalAuthService customerPortalAuthService;
    private final EmployeeCredentialService employeeCredentialService;
    private final PasswordResetService passwordResetService;

    /** Verifies the login, issues the token and returns it with the employee card and their branch. */
    public StaffPortalLoginResponse login(String username, String password) {
        StaffLoginResponse login = loginService.staffLogin(username, password);
        return new StaffPortalLoginResponse(login.getAccessToken(), login.getTokenType(), login.getExpiresIn(),
                PortalEmployee.of(login.getEmployee()), portalService.location(login.getEmployee().getBankLocationId()));
    }

    public void changePassword(String employeeNumber, ChangePasswordRequest request) {
        employeeCredentialService.changePassword(employeeNumber, request.getCurrentPassword(), request.getNewPassword());
    }

    public List<SecurityQuestionView> setSecurityQuestions(String employeeNumber, SetSecurityQuestionsRequest request) {
        return passwordResetService.setQuestions(CredentialOwnerType.EMPLOYEE, employeeNumber, request.getCurrentPassword(), request.getAnswers());
    }

    /** Every question an employee can choose from (the same catalog customers use). */
    public List<SecurityQuestionView> questionCatalog() {
        return customerPortalAuthService.questionCatalog();
    }

    public List<SecurityQuestionView> resetQuestions(String username) {
        return passwordResetService.questionsFor(CredentialOwnerType.EMPLOYEE, username);
    }

    public void resetPassword(PasswordResetRequest request) {
        passwordResetService.reset(CredentialOwnerType.EMPLOYEE, request.getUsername(), request.getAnswers(), request.getNewPassword());
    }
}
