package org.brite.banking.contoller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.StaffLoginResponse;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.service.LoginService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login for employees and customers. It verifies the username and 8-digit password and returns a signed JWT
 * access token plus who the caller is. Responses are {@code Cache-Control: no-store} because they carry a
 * credential. The employee token is required (as {@code Authorization: Bearer}) by every other staff endpoint;
 * nothing requires the customer token yet, so customer endpoints still use {@code X-Customer-Id} only as a rate-limit key.
 */
@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class LoginController {
    private final LoginService loginService;

    /** POST /v1/api/staff/login - 401 wrong credentials, 423 locked, 403 login or employee not active. */
    @PostMapping("/staff/login")
    public ResponseEntity<StaffLoginResponse> staffLogin(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(loginService.staffLogin(request.getUsername(), request.getPassword()));
    }

    /** POST /v1/api/customers/login - 401 wrong credentials, 423 locked, 403 login not active. */
    @PostMapping("/customers/login")
    public ResponseEntity<CustomerLoginResponse> customerLogin(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(loginService.customerLogin(request.getUsername(), request.getPassword()));
    }
}
