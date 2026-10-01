package org.brite.banking.contoller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.PasswordResetQuestionsRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerAccessService;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Changing a password while logged in, and forgotten-password reset with security questions, for employees ({@code /v1/api/staff/...}) and customers
 * ({@code /v1/api/customers/...}). The two reset calls are open (the user can't log in); changing the password and choosing the
 * questions need the caller's token and current password.
 */
@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class PasswordController {
    private final PasswordResetService passwordResetService;
    private final CustomerCredentialService customerCredentialService;
    private final EmployeeCredentialService employeeCredentialService;
    private final CustomerAccessService customerAccess;

    /** The three questions to answer. POST /v1/api/customers/password-reset/questions */
    @PostMapping("/customers/password-reset/questions")
    public ResponseEntity<List<SecurityQuestionView>> customerQuestions(@Valid @RequestBody PasswordResetQuestionsRequest request) {
        return ResponseEntity.ok(passwordResetService.questionsFor(CredentialOwnerType.CUSTOMER, request.getUsername()));
    }

    /** Resets a forgotten customer password: 204, 401 wrong answers, 423 reset locked, 403 login not active. POST /v1/api/customers/password-reset */
    @PostMapping("/customers/password-reset")
    public ResponseEntity<Void> customerReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.reset(CredentialOwnerType.CUSTOMER, request.getUsername(), request.getAnswers(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /** The logged-in customer picks and answers 3 questions (needs the current password). PUT /v1/api/customers/security-questions */
    @PutMapping("/customers/security-questions")
    public ResponseEntity<List<SecurityQuestionView>> setCustomerQuestions(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody SetSecurityQuestionsRequest request) {
        Long me = customerAccess.requireAuthenticated(customerId);
        return ResponseEntity.ok(passwordResetService.setQuestions(CredentialOwnerType.CUSTOMER, String.valueOf(me),
                request.getCurrentPassword(), request.getAnswers()));
    }

    /** The three questions to answer. POST /v1/api/staff/password-reset/questions */
    @PostMapping("/staff/password-reset/questions")
    public ResponseEntity<List<SecurityQuestionView>> staffQuestions(@Valid @RequestBody PasswordResetQuestionsRequest request) {
        return ResponseEntity.ok(passwordResetService.questionsFor(CredentialOwnerType.EMPLOYEE, request.getUsername()));
    }

    /** Resets a forgotten employee password: 204, 401 wrong answers, 423 reset locked, 403 login not active. POST /v1/api/staff/password-reset */
    @PostMapping("/staff/password-reset")
    public ResponseEntity<Void> staffReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.reset(CredentialOwnerType.EMPLOYEE, request.getUsername(), request.getAnswers(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /** The logged-in employee picks and answers 3 questions (needs the current password). PUT /v1/api/staff/security-questions */
    @PutMapping("/staff/security-questions")
    public ResponseEntity<List<SecurityQuestionView>> setStaffQuestions(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employeeNumber,
            @Valid @RequestBody SetSecurityQuestionsRequest request) {
        return ResponseEntity.ok(passwordResetService.setQuestions(CredentialOwnerType.EMPLOYEE, employeeNumber,
                request.getCurrentPassword(), request.getAnswers()));
    }

    /**
     * The logged-in customer changes their own password: 204, 401 wrong current password, 400 bad or unchanged new password, 423 locked,
     * 403 login not active. All earlier tokens (including this one) stop working. PUT /v1/api/customers/password
     */
    @PutMapping("/customers/password")
    public ResponseEntity<Void> changeCustomerPassword(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody ChangePasswordRequest request) {
        customerCredentialService.changePassword(customerAccess.requireAuthenticated(customerId), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /** As above for the logged-in employee. PUT /v1/api/staff/password */
    @PutMapping("/staff/password")
    public ResponseEntity<Void> changeStaffPassword(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employeeNumber,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (employeeNumber == null) {
            throw new InvalidTokenException(BankingMessages.AUTHENTICATION_REQUIRED);
        }
        employeeCredentialService.changePassword(employeeNumber, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
