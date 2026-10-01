package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.CustomerPortalAuthService;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.PasswordResetQuestionsRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerAccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer credentials in the BFF, in one controller: the customer's login, its status and its password.
 * <ul>
 *   <li>Customer portal ({@code /bff/v1/portal}): sign-in (open, no token yet); change password and choose security questions (customer token,
 *       the customer is taken from it, never the body); the question catalog and the two password-reset calls (open, the customer can't log in).</li>
 *   <li>Staff portal ({@code /bff/v1/staff}): create a customer's login, set its status and set its password (employee token, MANAGE_CUSTOMER_LOGINS
 *       enforced by the banking service before anything changes).</li>
 * </ul>
 * Every call goes through {@link CustomerPortalAuthService}; this class only routes, binds and wraps the response.
 */
@RestController
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class CustomerPortalAuthController {
    private final CustomerPortalAuthService authService;
    private final CustomerAccessService customerAccess;

    /**
     * Sign in and get the home screen in one call: 401 wrong credentials, 423 locked, 403 login not active. {@code Cache-Control: no-store}.
     * POST /bff/v1/portal/login?state=TX
     */
    @PostMapping("/portal/login")
    public ResponseEntity<PortalLoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                     @RequestParam(required = false) String state) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.login(request.getUsername(), request.getPassword(), state));
    }

    /** Create a customer's login (MANAGE_CUSTOMER_LOGINS): 201, 400 bad format/taken/already has one, 404 unknown customer. POST /bff/v1/staff/customers/11/login */
    @PostMapping("/staff/customers/{customerId}/login")
    public ResponseEntity<LoginStatusView> createLogin(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createLogin(employee, customerId, request));
    }

    /**
     * Set a customer's login status (ACTIVE, INACTIVE, LOCKED, SUSPENDED; only ACTIVE may transact), MANAGE_CUSTOMER_LOGINS. It applies at once, even to a
     * token the customer already holds. PUT /bff/v1/staff/customers/11/login-status
     */
    @PutMapping("/staff/customers/{customerId}/login-status")
    public ResponseEntity<LoginStatusView> changeStatus(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody ChangeLoginStatusRequest request) {
        return ResponseEntity.ok(authService.changeStatus(employee, customerId, request));
    }

    /** Change the logged-in customer's password; all earlier tokens (including this one) stop working. PUT /bff/v1/portal/password */
    @PutMapping("/portal/password")
    public ResponseEntity<Void> changePassword(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(customerAccess.requireAuthenticated(customerId), request);
        return ResponseEntity.noContent().build();
    }

    /** Choose and answer three security questions (needs the current password). PUT /bff/v1/portal/security-questions */
    @PutMapping("/portal/security-questions")
    public ResponseEntity<List<SecurityQuestionView>> setSecurityQuestions(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody SetSecurityQuestionsRequest request) {
        return ResponseEntity.ok(authService.setSecurityQuestions(customerAccess.requireAuthenticated(customerId), request));
    }

    /** All questions a customer can pick from. GET /bff/v1/portal/security-questions/catalog */
    @GetMapping("/portal/security-questions/catalog")
    public ResponseEntity<List<SecurityQuestionView>> questionCatalog() {
        return ResponseEntity.ok(authService.questionCatalog());
    }

    /** The three questions to answer for a forgotten password. POST /bff/v1/portal/password-reset/questions */
    @PostMapping("/portal/password-reset/questions")
    public ResponseEntity<List<SecurityQuestionView>> resetQuestions(@Valid @RequestBody PasswordResetQuestionsRequest request) {
        return ResponseEntity.ok(authService.resetQuestions(request.getUsername()));
    }

    /** Reset a forgotten password: 204, 401 wrong answers, 423 locked, 403 login not active. POST /bff/v1/portal/password-reset */
    @PostMapping("/portal/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /** Set a customer's password (MANAGE_CUSTOMER_LOGINS); their tokens stop working. PUT /bff/v1/staff/customers/11/password */
    @PutMapping("/staff/customers/{customerId}/password")
    public ResponseEntity<Void> setCustomerPassword(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody AdminSetPasswordRequest request) {
        authService.setPassword(employee, customerId, request);
        return ResponseEntity.noContent().build();
    }
}
