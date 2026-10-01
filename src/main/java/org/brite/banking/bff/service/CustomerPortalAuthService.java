package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.LoginService;
import org.brite.banking.service.PasswordResetService;
import org.brite.banking.service.StaffLoginService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Customer login, login status and password as the BFF serves them, for both portals: the customer's own sign-in (token + home screen in one
 * call), changing their password, choosing security questions and resetting a forgotten password, and, in the staff portal, a manager creating
 * a customer's login, setting its status and setting its password. No rules of its own: credential checks, lockout, status and token issuing
 * stay in {@link LoginService}, {@link CustomerCredentialService} and {@link PasswordResetService}, and the privilege checks and
 * administration rules in {@link StaffLoginService}, which run before anything changes; a failed login never builds the home screen.
 */
@Service
@RequiredArgsConstructor
public class CustomerPortalAuthService {
    private final LoginService loginService;
    private final PortalOrchestrationService portalService;
    private final StaffLoginService staffLoginService;
    private final CustomerCredentialService customerCredentialService;
    private final PasswordResetService passwordResetService;

    /** Verifies the login, issues the token and returns it with the home screen ({@code state} optionally narrows nearby branches). */
    public PortalLoginResponse login(String username, String password, String state) {
        CustomerLoginResponse login = loginService.customerLogin(username, password);
        PortalHomeResponse home = portalService.home(state, login.getCustomer().getCustomerId());
        return new PortalLoginResponse(login.getAccessToken(), login.getTokenType(), login.getExpiresIn(), login.getCustomer(), home);
    }

    /** Staff portal: a manager creates a customer's login (needs MANAGE_CUSTOMER_LOGINS, checked by the banking service first). */
    public LoginStatusView createLogin(String employee, Long customerId, LoginRequest request) {
        return staffLoginService.createCustomerLogin(employee, customerId, request);
    }

    /**
     * Staff portal: setting a customer's login status (ACTIVE, INACTIVE, LOCKED, SUSPENDED; only ACTIVE may transact). The privilege check
     * (MANAGE_CUSTOMER_LOGINS) and the status rules live in {@link StaffLoginService} and run before anything changes.
     */
    public LoginStatusView changeStatus(String employee, Long customerId, ChangeLoginStatusRequest request) {
        return staffLoginService.changeCustomerLoginStatus(employee, customerId, request);
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
        return SecurityQuestionView.catalog();
    }

    public List<SecurityQuestionView> resetQuestions(String username) {
        return passwordResetService.questionsFor(CredentialOwnerType.CUSTOMER, username);
    }

    public void resetPassword(PasswordResetRequest request) {
        passwordResetService.reset(CredentialOwnerType.CUSTOMER, request.getUsername(), request.getAnswers(), request.getNewPassword());
    }

    /** Staff portal: a manager sets a customer's password (needs MANAGE_CUSTOMER_LOGINS, checked by the banking service first). */
    public void setPassword(String employee, Long customerId, AdminSetPasswordRequest request) {
        staffLoginService.setCustomerPassword(employee, customerId, request);
    }
}
