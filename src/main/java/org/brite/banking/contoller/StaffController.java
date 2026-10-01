package org.brite.banking.contoller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.StaffAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee-facing endpoints. Every call names the acting employee in {@code X-Employee-Number};
 * {@link EmployeeService} then enforces the role's privileges (403 if not allowed).
 */
@RestController
@RequestMapping("/v1/api/staff")
@RequiredArgsConstructor
public class StaffController {
    private final StaffAccountService staffAccountService;
    private final EmployeeService employeeService;

    /**
     * Needs WITHDRAW (teller and up). The transaction records the employee and the branch/ATM
     * ({@code locationId}, default the employee's own branch).
     * POST /v1/api/staff/accounts/withdraw?locationId=
     */
    @PostMapping("/accounts/withdraw")
    public ResponseEntity<Account> withdraw(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                            @RequestParam(required = false) Long locationId,
                                            @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(staffAccountService.withdraw(employee, locationId, request));
    }

    /** Needs DEPOSIT (teller and up); records employee and branch/ATM like withdraw. POST /v1/api/staff/accounts/deposit?locationId= */
    @PostMapping("/accounts/deposit")
    public ResponseEntity<Account> deposit(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                           @RequestParam(required = false) Long locationId,
                                           @Valid @RequestBody DepositForm request) {
        return ResponseEntity.ok(staffAccountService.deposit(employee, locationId, request));
    }

    /** Needs SUSPEND_ACCOUNT (manager and up). POST /v1/api/staff/accounts/{accountNumber}/suspend */
    @PostMapping("/accounts/{accountNumber}/suspend")
    public ResponseEntity<Account> suspend(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                           @PathVariable String accountNumber,
                                           @Valid @RequestBody SuspendAccountRequest request) {
        return ResponseEntity.ok(staffAccountService.suspend(employee, accountNumber, request));
    }

    /** Needs UPDATE_SUSPENSION (manager and up). PATCH /v1/api/staff/accounts/{accountNumber}/suspension */
    @PatchMapping("/accounts/{accountNumber}/suspension")
    public ResponseEntity<Account> updateSuspension(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                                    @PathVariable String accountNumber,
                                                    @Valid @RequestBody UpdateSuspensionRequest request) {
        return ResponseEntity.ok(staffAccountService.updateSuspension(employee, accountNumber, request));
    }

    /** Needs REACTIVATE_ACCOUNT (manager and up). POST /v1/api/staff/accounts/{accountNumber}/reactivate */
    @PostMapping("/accounts/{accountNumber}/reactivate")
    public ResponseEntity<Account> reactivate(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                              @PathVariable String accountNumber) {
        return ResponseEntity.ok(staffAccountService.reactivate(employee, accountNumber));
    }

    /** Needs CLOSE_ACCOUNT (manager and up). POST /v1/api/staff/accounts/{accountNumber}/close */
    @PostMapping("/accounts/{accountNumber}/close")
    public ResponseEntity<Account> close(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                         @PathVariable String accountNumber) {
        return ResponseEntity.ok(staffAccountService.close(employee, accountNumber));
    }

    /** Needs MANAGE_EMPLOYEES (area manager). GET /v1/api/staff/employees?role=&page=&size=&sort= */
    @GetMapping("/employees")
    public ResponseEntity<Page<Employee>> listEmployees(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                                        @RequestParam(required = false) EmployeeRole role,
                                                        @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return ResponseEntity.ok(employeeService.listEmployees(employee, role, pageable));
    }

    /** Own profile, or any profile with MANAGE_EMPLOYEES. GET /v1/api/staff/employees/{employeeNumber} */
    @GetMapping("/employees/{employeeNumber}")
    public ResponseEntity<Employee> getEmployee(@RequestHeader(value = EmployeeService.EMPLOYEE_HEADER, required = false) String employee,
                                                @PathVariable String employeeNumber) {
        return ResponseEntity.ok(employeeService.getEmployee(employee, employeeNumber));
    }
}
