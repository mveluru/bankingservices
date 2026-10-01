package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.LoginService;
import org.brite.banking.service.PasswordResetService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * The portal's account-access calls (login, change password, security questions, forgotten-password reset), composed from the
 * banking services in process. Like the rest of the BFF it holds no rules: credential checks, lockout, status and token issuing all
 * stay in {@link LoginService}, {@link CustomerCredentialService} and {@link PasswordResetService}; any failure they throw
 * propagates (and a failed login never builds the home screen).
 */
@Service
@RequiredArgsConstructor
public class PortalAuthService {
    private final LoginService loginService;
    private final PortalOrchestrationService portalService;
    private final CustomerCredentialService customerCredentialService;
    private final PasswordResetService passwordResetService;

    /** Verifies the login, issues the token and returns it together with the home screen ({@code state} optionally narrows nearby branches). */
    public PortalLoginResponse login(String username, String password, String state) {
        CustomerLoginResponse login = loginService.customerLogin(username, password);
        PortalHomeResponse home = portalService.home(state, login.getCustomer().getCustomerId());
        return new PortalLoginResponse(login.getAccessToken(), login.getTokenType(), login.getExpiresIn(), login.getCustomer(), home);
    }

    public void changePassword(Long customerId, ChangePasswordRequest request) {
        customerCredentialService.changePassword(customerId, request.getCurrentPassword(), request.getNewPassword());
    }

    public List<SecurityQuestionView> setSecurityQuestions(Long customerId, SetSecurityQuestionsRequest request) {
        return passwordResetService.setQuestions(CredentialOwnerType.CUSTOMER, String.valueOf(customerId),
                request.getCurrentPassword(), request.getAnswers());
    }

    /** Every question a customer can choose from, for the "pick three" screen. */
    public List<SecurityQuestionView> questionCatalog() {
        return Arrays.stream(SecurityQuestion.values()).map(SecurityQuestionView::of).toList();
    }

    public List<SecurityQuestionView> resetQuestions(String username) {
        return passwordResetService.questionsFor(CredentialOwnerType.CUSTOMER, username);
    }

    public void resetPassword(PasswordResetRequest request) {
        passwordResetService.reset(CredentialOwnerType.CUSTOMER, request.getUsername(), request.getAnswers(), request.getNewPassword());
    }
}
