package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.dto.PortalEmployee;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.StaffAccountService;
import org.brite.banking.service.StaffLoginService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The staff portal's account and people calls, composed from the banking staff services in process. Every privilege and status check
 * stays in {@link StaffAccountService}, {@link StaffLoginService} and {@link EmployeeService} and runs <em>before</em> anything changes;
 * this class only delegates and, after an account action, rebuilds the account overview so the screen redraws from one response (if the
 * banking service throws, no overview is built).
 */
@Service
@RequiredArgsConstructor
public class StaffPortalService {
    private final StaffAccountService staffAccountService;
    private final StaffLoginService staffLoginService;
    private final EmployeeService employeeService;
    private final PortalOrchestrationService portalService;

    /** An account's overview for staff (needs VIEW_ACCOUNT). Same shape as the customer overview; suspension notes are never included. */
    public AccountOverviewResponse overview(String employee, String accountNumber, Integer days) {
        employeeService.requirePrivilege(employee, EmployeePrivilege.VIEW_ACCOUNT);
        return portalService.overview(accountNumber, days);
    }

    public AccountOverviewResponse withdraw(String employee, Long locationId, WithdrawalRequest request) {
        staffAccountService.withdraw(employee, locationId, request);
        return portalService.overview(request.getAccountNumber(), null);
    }

    public AccountOverviewResponse deposit(String employee, Long locationId, DepositForm request) {
        staffAccountService.deposit(employee, locationId, request);
        return portalService.overview(request.getAccountNumber(), null);
    }

    public AccountOverviewResponse suspend(String employee, String accountNumber, SuspendAccountRequest request) {
        staffAccountService.suspend(employee, accountNumber, request);
        return portalService.overview(accountNumber, null);
    }

    public AccountOverviewResponse updateSuspension(String employee, String accountNumber, UpdateSuspensionRequest request) {
        staffAccountService.updateSuspension(employee, accountNumber, request);
        return portalService.overview(accountNumber, null);
    }

    public AccountOverviewResponse reactivate(String employee, String accountNumber) {
        staffAccountService.reactivate(employee, accountNumber);
        return portalService.overview(accountNumber, null);
    }

    public AccountOverviewResponse close(String employee, String accountNumber) {
        staffAccountService.close(employee, accountNumber);
        return portalService.overview(accountNumber, null);
    }

    /** Employees as portal cards (needs MANAGE_EMPLOYEES). */
    public Page<PortalEmployee> employees(String employee, EmployeeRole role, Pageable pageable) {
        return employeeService.listEmployees(employee, role, pageable).map(PortalEmployee::of);
    }

    /** One employee card: their own, or anyone's with MANAGE_EMPLOYEES. */
    public PortalEmployee employee(String employee, String employeeNumber) {
        return PortalEmployee.of(employeeService.getEmployee(employee, employeeNumber));
    }

    public LoginStatusView changeEmployeeLoginStatus(String employee, String employeeNumber, ChangeLoginStatusRequest request) {
        return staffLoginService.changeEmployeeLoginStatus(employee, employeeNumber, request);
    }

    public LoginStatusView changeCustomerLoginStatus(String employee, Long customerId, ChangeLoginStatusRequest request) {
        return staffLoginService.changeCustomerLoginStatus(employee, customerId, request);
    }

    public LoginStatusView createCustomerLogin(String employee, Long customerId, LoginRequest request) {
        return staffLoginService.createCustomerLogin(employee, customerId, request);
    }

    public void setEmployeePassword(String employee, String employeeNumber, AdminSetPasswordRequest request) {
        staffLoginService.setEmployeePassword(employee, employeeNumber, request);
    }

    public void setCustomerPassword(String employee, Long customerId, AdminSetPasswordRequest request) {
        staffLoginService.setCustomerPassword(employee, customerId, request);
    }
}
