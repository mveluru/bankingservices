package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.CustomerLoginPortalService;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.request.LoginRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer logins in the BFF. The customer's own sign-in lives in the customer portal ({@code /bff/v1/portal}, open, no token yet); creating a
 * customer's login is a staff-portal call ({@code /bff/v1/staff}, employee token, MANAGE_CUSTOMER_LOGINS enforced by the banking service).
 */
@RestController
@RequestMapping("/bff/v1")
@RequiredArgsConstructor
public class CustomerLoginController {
    private final CustomerLoginPortalService service;

    /**
     * Sign in and get the home screen in one call: 401 wrong credentials, 423 locked, 403 login not active. {@code Cache-Control: no-store}.
     * POST /bff/v1/portal/login?state=TX
     */
    @PostMapping("/portal/login")
    public ResponseEntity<PortalLoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                     @RequestParam(required = false) String state) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.login(request.getUsername(), request.getPassword(), state));
    }

    /** Create a customer's login (MANAGE_CUSTOMER_LOGINS): 201, 400 bad format/taken/already has one, 404 unknown customer. POST /bff/v1/staff/customers/11/login */
    @PostMapping("/staff/customers/{customerId}/login")
    public ResponseEntity<LoginStatusView> createLogin(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLogin(employee, customerId, request));
    }
}
