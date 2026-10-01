package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.dto.PortalEmployee;
import org.brite.banking.bff.service.StaffPortalService;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend-for-frontend endpoints for the staff portal: the employee counterpart of {@link PortalController}. Every call needs
 * {@code Authorization: Bearer <employee token>} ({@link StaffAuthenticationFilter}) and takes the acting employee from it; the banking
 * services enforce the privilege before anything changes (403) and account actions return the refreshed account overview. Customer logins,
 * their status and their passwords are served by {@link CustomerPortalAuthController}; an employee's own login status and password calls are in {@link StaffPortalAuthController}.
 */
@RestController
@RequestMapping("/bff/v1/staff")
@RequiredArgsConstructor
public class StaffPortalController {
    private final StaffPortalService staffPortalService;

    private static final String EMPLOYEE = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE;

    /** Account overview for staff (needs VIEW_ACCOUNT). GET /bff/v1/staff/accounts/CH-0000088291/overview?days=30 */
    @GetMapping("/accounts/{accountNumber}/overview")
    public ResponseEntity<AccountOverviewResponse> overview(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                            @PathVariable String accountNumber,
                                                            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(staffPortalService.overview(employee, accountNumber, days));
    }

    /** Withdraw as an employee (WITHDRAW); the transaction records them and the branch/ATM ({@code locationId}, default their branch). POST /bff/v1/staff/accounts/withdraw?locationId= */
    @PostMapping("/accounts/withdraw")
    public ResponseEntity<AccountOverviewResponse> withdraw(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                            @RequestParam(required = false) Long locationId,
                                                            @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(staffPortalService.withdraw(employee, locationId, request));
    }

    /** Deposit as an employee (DEPOSIT). POST /bff/v1/staff/accounts/deposit?locationId= */
    @PostMapping("/accounts/deposit")
    public ResponseEntity<AccountOverviewResponse> deposit(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                           @RequestParam(required = false) Long locationId,
                                                           @Valid @RequestBody DepositForm request) {
        return ResponseEntity.ok(staffPortalService.deposit(employee, locationId, request));
    }

    /** Suspend an account (SUSPEND_ACCOUNT, managers and up). POST /bff/v1/staff/accounts/CH-0000010001/suspend */
    @PostMapping("/accounts/{accountNumber}/suspend")
    public ResponseEntity<AccountOverviewResponse> suspend(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                           @PathVariable String accountNumber,
                                                           @Valid @RequestBody SuspendAccountRequest request) {
        return ResponseEntity.ok(staffPortalService.suspend(employee, accountNumber, request));
    }

    /** Change a suspension's end and/or notes (UPDATE_SUSPENSION). PATCH /bff/v1/staff/accounts/CH-0000010001/suspension */
    @PatchMapping("/accounts/{accountNumber}/suspension")
    public ResponseEntity<AccountOverviewResponse> updateSuspension(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                                    @PathVariable String accountNumber,
                                                                    @Valid @RequestBody UpdateSuspensionRequest request) {
        return ResponseEntity.ok(staffPortalService.updateSuspension(employee, accountNumber, request));
    }

    /** Lift a suspension (REACTIVATE_ACCOUNT). POST /bff/v1/staff/accounts/CH-0000010001/reactivate */
    @PostMapping("/accounts/{accountNumber}/reactivate")
    public ResponseEntity<AccountOverviewResponse> reactivate(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                              @PathVariable String accountNumber) {
        return ResponseEntity.ok(staffPortalService.reactivate(employee, accountNumber));
    }

    /** Close an account (CLOSE_ACCOUNT, irreversible). POST /bff/v1/staff/accounts/CH-0000010001/close */
    @PostMapping("/accounts/{accountNumber}/close")
    public ResponseEntity<AccountOverviewResponse> close(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                         @PathVariable String accountNumber) {
        return ResponseEntity.ok(staffPortalService.close(employee, accountNumber));
    }

    /** Employee cards (MANAGE_EMPLOYEES). GET /bff/v1/staff/employees?role=&page=&size=&sort= */
    @GetMapping("/employees")
    public ResponseEntity<Page<PortalEmployee>> employees(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                          @RequestParam(required = false) EmployeeRole role,
                                                          @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return ResponseEntity.ok(staffPortalService.employees(employee, role, pageable));
    }

    /** One employee card: your own, or anyone's with MANAGE_EMPLOYEES. GET /bff/v1/staff/employees/EMP-000010 */
    @GetMapping("/employees/{employeeNumber}")
    public ResponseEntity<PortalEmployee> employee(@RequestAttribute(value = EMPLOYEE, required = false) String employee,
                                                   @PathVariable String employeeNumber) {
        return ResponseEntity.ok(staffPortalService.employee(employee, employeeNumber));
    }
}
