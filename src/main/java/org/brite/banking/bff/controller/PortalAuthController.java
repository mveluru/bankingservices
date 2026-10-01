package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.PortalAuthService;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.PasswordResetQuestionsRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerAccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The portal's sign-in and password calls. Login, the question catalog and the two password-reset calls are open (the customer has
 * no token yet); changing the password and choosing security questions need the customer token
 * ({@link CustomerAuthenticationFilter}) and take the customer from it, never from the body.
 */
@RestController
@RequestMapping("/bff/v1/portal")
@RequiredArgsConstructor
public class PortalAuthController {
    private final PortalAuthService authService;
    private final CustomerAccessService customerAccess;

    /**
     * Login and the home screen in one call: 401 wrong credentials, 423 locked, 403 login not active. {@code Cache-Control: no-store}.
     * POST /bff/v1/portal/login?state=TX
     */
    @PostMapping("/login")
    public ResponseEntity<PortalLoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                     @RequestParam(required = false) String state) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.login(request.getUsername(), request.getPassword(), state));
    }

    /**
     * Change the logged-in customer's password; all earlier tokens (including this one) stop working, so log in again.
     * PUT /bff/v1/portal/password
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(customerAccess.requireAuthenticated(customerId), request);
        return ResponseEntity.noContent().build();
    }

    /** Choose and answer three security questions (needs the current password). PUT /bff/v1/portal/security-questions */
    @PutMapping("/security-questions")
    public ResponseEntity<List<SecurityQuestionView>> setSecurityQuestions(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @Valid @RequestBody SetSecurityQuestionsRequest request) {
        return ResponseEntity.ok(authService.setSecurityQuestions(customerAccess.requireAuthenticated(customerId), request));
    }

    /** All questions a customer can pick from. GET /bff/v1/portal/security-questions/catalog */
    @GetMapping("/security-questions/catalog")
    public ResponseEntity<List<SecurityQuestionView>> questionCatalog() {
        return ResponseEntity.ok(authService.questionCatalog());
    }

    /** The three questions to answer for a forgotten password. POST /bff/v1/portal/password-reset/questions */
    @PostMapping("/password-reset/questions")
    public ResponseEntity<List<SecurityQuestionView>> resetQuestions(@Valid @RequestBody PasswordResetQuestionsRequest request) {
        return ResponseEntity.ok(authService.resetQuestions(request.getUsername()));
    }

    /** Reset a forgotten password with the answers: 204, 401 wrong answers, 423 locked, 403 login not active. POST /bff/v1/portal/password-reset */
    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
