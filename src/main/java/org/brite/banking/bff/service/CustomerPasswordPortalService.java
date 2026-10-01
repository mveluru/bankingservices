package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.PasswordResetService;
import org.brite.banking.service.StaffLoginService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Customer passwords as the BFF serves them: the customer changing their own, choosing security questions and resetting a forgotten password,
 * and a manager setting a customer's password in the staff portal. Delegation only; every rule (current password check, lockout, status,
 * token invalidation, privilege) stays in {@link CustomerCredentialService}, {@link PasswordResetService} and {@link StaffLoginService}.
 */
@Service
@RequiredArgsConstructor
public class CustomerPasswordPortalService {
    private final CustomerCredentialService customerCredentialService;
    private final PasswordResetService passwordResetService;
    private final StaffLoginService staffLoginService;

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
