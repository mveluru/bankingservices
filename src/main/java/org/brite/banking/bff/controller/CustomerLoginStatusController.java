package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.service.CustomerLoginStatusPortalService;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff portal: a customer's login status (ACTIVE, INACTIVE, LOCKED, SUSPENDED; only ACTIVE may transact). Needs the employee token and
 * MANAGE_CUSTOMER_LOGINS, which the banking service enforces before anything changes. The change applies at once, even to a token the customer
 * already holds.
 */
@RestController
@RequestMapping("/bff/v1/staff/customers")
@RequiredArgsConstructor
public class CustomerLoginStatusController {
    private final CustomerLoginStatusPortalService service;

    /** PUT /bff/v1/staff/customers/11/login-status */
    @PutMapping("/{customerId}/login-status")
    public ResponseEntity<LoginStatusView> changeStatus(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody ChangeLoginStatusRequest request) {
        return ResponseEntity.ok(service.changeStatus(employee, customerId, request));
    }
}
