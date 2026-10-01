package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.StaffPortalLoginResponse;
import org.brite.banking.bff.service.StaffPortalAuthService;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.PasswordResetQuestionsRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The staff portal's sign-in and password calls (the employee counterpart of {@link PortalAuthController}). Login, the question catalog and
 * the two password-reset calls are open; changing the password and choosing security questions need the employee token
 * ({@link StaffAuthenticationFilter}) and take the employee from it, never from the body.
 */
@RestController
@RequestMapping("/bff/v1/staff")
@RequiredArgsConstructor
public class StaffPortalAuthController {
    private final StaffPortalAuthService authService;

    /** Login with the employee card and branch in one call: 401/403/423/400. {@code Cache-Control: no-store}. POST /bff/v1/staff/login */
    @PostMapping("/login")
    public ResponseEntity<StaffPortalLoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(request.getUsername(), request.getPassword()));
    }

    /** Change the logged-in employee's password; all earlier tokens (including this one) stop working. PUT /bff/v1/staff/password */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(employee, request);
        return ResponseEntity.noContent().build();
    }

    /** Choose and answer three security questions (needs the current password). PUT /bff/v1/staff/security-questions */
    @PutMapping("/security-questions")
    public ResponseEntity<List<SecurityQuestionView>> setSecurityQuestions(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @Valid @RequestBody SetSecurityQuestionsRequest request) {
        return ResponseEntity.ok(authService.setSecurityQuestions(employee, request));
    }

    /** All questions an employee can pick from. GET /bff/v1/staff/security-questions/catalog */
    @GetMapping("/security-questions/catalog")
    public ResponseEntity<List<SecurityQuestionView>> questionCatalog() {
        return ResponseEntity.ok(authService.questionCatalog());
    }

    /** The three questions to answer for a forgotten password. POST /bff/v1/staff/password-reset/questions */
    @PostMapping("/password-reset/questions")
    public ResponseEntity<List<SecurityQuestionView>> resetQuestions(@Valid @RequestBody PasswordResetQuestionsRequest request) {
        return ResponseEntity.ok(authService.resetQuestions(request.getUsername()));
    }

    /** Reset a forgotten password: 204, 401 wrong answers, 423 locked, 403 login not active. POST /bff/v1/staff/password-reset */
    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
