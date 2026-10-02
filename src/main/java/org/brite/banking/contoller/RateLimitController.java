package org.brite.banking.contoller;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CustomerRateLimitView;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.service.CustomerAccessService;
import org.brite.banking.service.CustomerQuotaService;
import org.brite.banking.service.StaffLoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A logged-in customer's or employee's view of their <em>own</em> daily request limit and today's usage. The identity comes only from the
 * verified token (never a path or body), so nobody can read someone else's usage here; managers use the {@code /employees/{n}/rate-limit} and
 * {@code /customers/{id}/rate-limit} staff calls for that.
 */
@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class RateLimitController {
    private final CustomerQuotaService customerQuotaService;
    private final StaffLoginService staffLoginService;
    private final CustomerAccessService customerAccess;

    /** The calling customer's daily request limit and today's usage. GET /v1/api/customers/rate-limit */
    @GetMapping("/customers/rate-limit")
    public ResponseEntity<CustomerRateLimitView> myCustomerRateLimit(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId) {
        return ResponseEntity.ok(customerQuotaService.view(customerAccess.requireAuthenticated(customerId)));
    }

    /** The calling employee's daily request limit and today's usage (an ACTIVE employee). GET /v1/api/staff/rate-limit */
    @GetMapping("/staff/rate-limit")
    public ResponseEntity<EmployeeRateLimitView> myEmployeeRateLimit(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee) {
        return ResponseEntity.ok(staffLoginService.ownRateLimit(employee));
    }
}
