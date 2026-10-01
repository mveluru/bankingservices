package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.service.CustomerPasswordPortalService;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.PasswordResetQuestionsRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer passwords in the BFF. Customer portal ({@code /bff/v1/portal}): change password and choose security questions need the customer token
 * and take the customer from it, never the body; the question catalog and the two password-reset calls are open (the customer can't log in).
 * Staff portal ({@code /bff/v1/staff}): a manager sets a customer's password (employee token, MANAGE_CUSTOMER_LOGINS enforced by the banking service).
 */
@RestController
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class CustomerPasswordController {
    private final CustomerPasswordPortalService service;
    private final CustomerAccessService customerAccess;

    /** Change the logged-in customer's password; all earlier tokens (including this one) stop working. PUT /bff/v1/portal/password */
    @PutMapping("/portal/password")
    public ResponseEntity<Void> changePassword(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(customerAccess.requireAuthenticated(customerId), request);
        return ResponseEntity.noContent().build();
    }

    /** Choose and answer three security questions (needs the current password). PUT /bff/v1/portal/security-questions */
    @PutMapping("/portal/security-questions")
    public ResponseEntity<List<SecurityQuestionView>> setSecurityQuestions(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody SetSecurityQuestionsRequest request) {
        return ResponseEntity.ok(service.setSecurityQuestions(customerAccess.requireAuthenticated(customerId), request));
    }

    /** All questions a customer can pick from. GET /bff/v1/portal/security-questions/catalog */
    @GetMapping("/portal/security-questions/catalog")
    public ResponseEntity<List<SecurityQuestionView>> questionCatalog() {
        return ResponseEntity.ok(service.questionCatalog());
    }

    /** The three questions to answer for a forgotten password. POST /bff/v1/portal/password-reset/questions */
    @PostMapping("/portal/password-reset/questions")
    public ResponseEntity<List<SecurityQuestionView>> resetQuestions(@Valid @RequestBody PasswordResetQuestionsRequest request) {
        return ResponseEntity.ok(service.resetQuestions(request.getUsername()));
    }

    /** Reset a forgotten password: 204, 401 wrong answers, 423 locked, 403 login not active. POST /bff/v1/portal/password-reset */
    @PostMapping("/portal/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        service.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /** Set a customer's password (MANAGE_CUSTOMER_LOGINS); their tokens stop working. PUT /bff/v1/staff/customers/11/password */
    @PutMapping("/staff/customers/{customerId}/password")
    public ResponseEntity<Void> setCustomerPassword(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody AdminSetPasswordRequest request) {
        service.setPassword(employee, customerId, request);
        return ResponseEntity.noContent().build();
    }
}
