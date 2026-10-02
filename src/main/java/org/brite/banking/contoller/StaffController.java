package org.brite.banking.contoller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.Account;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.CustomerRateLimitView;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.SetRateLimitRequest;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.StaffAccountService;
import org.brite.banking.service.StaffLoginService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee-facing endpoints. Every call except the login needs {@code Authorization: Bearer <token>}; the
 * {@link StaffAuthenticationFilter} verifies it and passes the employee number in a request attribute, and
 * {@link EmployeeService} then enforces the role's privileges (403 if not allowed).
 */
@RestController
@RequestMapping("/v1/api/staff")
@RequiredArgsConstructor
public class StaffController {
    private final StaffAccountService staffAccountService;
    private final EmployeeService employeeService;
    private final StaffLoginService staffLoginService;

    /**
     * Needs WITHDRAW (teller and up). The transaction records the employee and the branch/ATM
     * ({@code locationId}, default the employee's own branch).
     * POST /v1/api/staff/accounts/withdraw?locationId=
     */
    @PostMapping("/accounts/withdraw")
    public ResponseEntity<Account> withdraw(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                            @RequestParam(required = false) Long locationId,
                                            @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(staffAccountService.withdraw(employee, locationId, request));
    }

    /** Needs DEPOSIT (teller and up); records employee and branch/ATM like withdraw. POST /v1/api/staff/accounts/deposit?locationId= */
    @PostMapping("/accounts/deposit")
    public ResponseEntity<Account> deposit(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                           @RequestParam(required = false) Long locationId,
                                           @Valid @RequestBody DepositForm request) {
        return ResponseEntity.ok(staffAccountService.deposit(employee, locationId, request));
    }

    /** Needs SUSPEND_ACCOUNT (manager and up). POST /v1/api/staff/accounts/{accountNumber}/suspend */
    @PostMapping("/accounts/{accountNumber}/suspend")
    public ResponseEntity<Account> suspend(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                           @PathVariable String accountNumber,
                                           @Valid @RequestBody SuspendAccountRequest request) {
        return ResponseEntity.ok(staffAccountService.suspend(employee, accountNumber, request));
    }

    /** Needs UPDATE_SUSPENSION (manager and up). PATCH /v1/api/staff/accounts/{accountNumber}/suspension */
    @PatchMapping("/accounts/{accountNumber}/suspension")
    public ResponseEntity<Account> updateSuspension(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                                    @PathVariable String accountNumber,
                                                    @Valid @RequestBody UpdateSuspensionRequest request) {
        return ResponseEntity.ok(staffAccountService.updateSuspension(employee, accountNumber, request));
    }

    /** Needs REACTIVATE_ACCOUNT (manager and up). POST /v1/api/staff/accounts/{accountNumber}/reactivate */
    @PostMapping("/accounts/{accountNumber}/reactivate")
    public ResponseEntity<Account> reactivate(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                              @PathVariable String accountNumber) {
        return ResponseEntity.ok(staffAccountService.reactivate(employee, accountNumber));
    }

    /** Needs CLOSE_ACCOUNT (manager and up). POST /v1/api/staff/accounts/{accountNumber}/close */
    @PostMapping("/accounts/{accountNumber}/close")
    public ResponseEntity<Account> close(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                         @PathVariable String accountNumber) {
        return ResponseEntity.ok(staffAccountService.close(employee, accountNumber));
    }

    /** Needs MANAGE_EMPLOYEES (area manager). GET /v1/api/staff/employees?role=&page=&size=&sort= */
    @GetMapping("/employees")
    public ResponseEntity<Page<Employee>> listEmployees(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                                        @RequestParam(required = false) EmployeeRole role,
                                                        @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return ResponseEntity.ok(employeeService.listEmployees(employee, role, pageable));
    }

    /** Own profile, or any profile with MANAGE_EMPLOYEES. GET /v1/api/staff/employees/{employeeNumber} */
    @GetMapping("/employees/{employeeNumber}")
    public ResponseEntity<Employee> getEmployee(@RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
                                                @PathVariable String employeeNumber) {
        return ResponseEntity.ok(employeeService.getEmployee(employee, employeeNumber));
    }

    /**
     * Needs MANAGE_EMPLOYEES (area manager). Sets an employee's login to ACTIVE, INACTIVE, LOCKED or SUSPENDED;
     * only ACTIVE may perform transactions. PUT /v1/api/staff/employees/{employeeNumber}/login-status
     */
    @PutMapping("/employees/{employeeNumber}/login-status")
    public ResponseEntity<LoginStatusView> changeEmployeeLoginStatus(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable String employeeNumber,
            @Valid @RequestBody ChangeLoginStatusRequest request) {
        return ResponseEntity.ok(staffLoginService.changeEmployeeLoginStatus(employee, employeeNumber, request));
    }

    /**
     * Needs MANAGE_EMPLOYEES (area manager). Sets an employee's password to a new 8-digit value (for a forgotten password when no
     * security questions are set); their tokens stop working. PUT /v1/api/staff/employees/{employeeNumber}/password
     */
    @PutMapping("/employees/{employeeNumber}/password")
    public ResponseEntity<Void> setEmployeePassword(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable String employeeNumber,
            @Valid @RequestBody AdminSetPasswordRequest request) {
        staffLoginService.setEmployeePassword(employee, employeeNumber, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Needs MANAGE_CUSTOMER_LOGINS (manager and up). Sets a customer's password to a new 8-digit value; their tokens stop working.
     * PUT /v1/api/staff/customers/{customerId}/password
     */
    @PutMapping("/customers/{customerId}/password")
    public ResponseEntity<Void> setCustomerPassword(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody AdminSetPasswordRequest request) {
        staffLoginService.setCustomerPassword(employee, customerId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Needs MANAGE_CUSTOMER_LOGINS (manager and up). Creates a customer's login (username + 8-digit password); customers
     * need one to use the protected account/portal endpoints. POST /v1/api/staff/customers/{customerId}/login
     */
    @PostMapping("/customers/{customerId}/login")
    public ResponseEntity<LoginStatusView> createCustomerLogin(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffLoginService.createCustomerLogin(employee, customerId, request));
    }

    /**
     * Needs MANAGE_CUSTOMER_LOGINS (manager and up). Sets a customer's login status; a customer whose login isn't
     * ACTIVE can't deposit or withdraw. PUT /v1/api/staff/customers/{customerId}/login-status
     */
    @PutMapping("/customers/{customerId}/login-status")
    public ResponseEntity<LoginStatusView> changeCustomerLoginStatus(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody ChangeLoginStatusRequest request) {
        return ResponseEntity.ok(staffLoginService.changeCustomerLoginStatus(employee, customerId, request));
    }

    /**
     * Needs MANAGE_CUSTOMER_LOGINS (manager and up). A customer's daily request limit (their own, else the default) and today's usage.
     * GET /v1/api/staff/customers/{customerId}/rate-limit
     */
    @GetMapping("/customers/{customerId}/rate-limit")
    public ResponseEntity<CustomerRateLimitView> customerRateLimit(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId) {
        return ResponseEntity.ok(staffLoginService.customerRateLimit(employee, customerId));
    }

    /**
     * Needs MANAGE_CUSTOMER_LOGINS (manager and up). Gives a customer their own daily request limit (1 to 1,000,000), or with
     * {@code {"maxRequestsPerDay": null}} (or {@code {}}) puts them back on the default. PUT /v1/api/staff/customers/{customerId}/rate-limit
     */
    @PutMapping("/customers/{customerId}/rate-limit")
    public ResponseEntity<CustomerRateLimitView> setCustomerRateLimit(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable Long customerId,
            @Valid @RequestBody SetRateLimitRequest request) {
        return ResponseEntity.ok(staffLoginService.setCustomerRateLimit(employee, customerId, request));
    }

    /**
     * Needs MANAGE_EMPLOYEES (area manager). An employee's daily request limit (their own, else the default) and today's usage.
     * GET /v1/api/staff/employees/{employeeNumber}/rate-limit
     */
    @GetMapping("/employees/{employeeNumber}/rate-limit")
    public ResponseEntity<EmployeeRateLimitView> employeeRateLimit(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable String employeeNumber) {
        return ResponseEntity.ok(staffLoginService.employeeRateLimit(employee, employeeNumber));
    }

    /**
     * Needs MANAGE_EMPLOYEES (area manager). Gives an employee their own daily request limit (1 to 1,000,000), or with
     * {@code {"maxRequestsPerDay": null}} (or {@code {}}) puts them back on the default. PUT /v1/api/staff/employees/{employeeNumber}/rate-limit
     */
    @PutMapping("/employees/{employeeNumber}/rate-limit")
    public ResponseEntity<EmployeeRateLimitView> setEmployeeRateLimit(
            @RequestAttribute(value = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, required = false) String employee,
            @PathVariable String employeeNumber,
            @Valid @RequestBody SetRateLimitRequest request) {
        return ResponseEntity.ok(staffLoginService.setEmployeeRateLimit(employee, employeeNumber, request));
    }
}
