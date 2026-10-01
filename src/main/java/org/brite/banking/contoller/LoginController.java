package org.brite.banking.contoller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.Employee;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.EmployeeCredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login for employees and customers. It verifies the username and 8-digit password and returns who the
 * caller is; it does not issue a session or token, so other endpoints still identify the caller by
 * {@code X-Employee-Number} / {@code X-Customer-Id}.
 */
@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class LoginController {
    private final EmployeeCredentialService employeeCredentialService;
    private final CustomerCredentialService customerCredentialService;

    /** POST /v1/api/staff/login - 401 wrong credentials, 423 locked, 403 login or employee not active. */
    @PostMapping("/staff/login")
    public ResponseEntity<Employee> staffLogin(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(employeeCredentialService.verify(request.getUsername(), request.getPassword()));
    }

    /** POST /v1/api/customers/login - 401 wrong credentials, 423 locked, 403 login not active. */
    @PostMapping("/customers/login")
    public ResponseEntity<AuthenticatedCustomer> customerLogin(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(customerCredentialService.verify(request.getUsername(), request.getPassword()));
    }
}
