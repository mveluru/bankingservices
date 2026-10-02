package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.dto.OpenAccountResponse;
import org.brite.banking.bff.dto.PortalEmployee;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.CustomerRateLimitView;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.AccountRegistrationRequest;
import org.brite.banking.request.SetRateLimitRequest;
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

    /**
     * Opens an account for a customer at the office (needs OPEN_ACCOUNT, checked by the banking staff service before anything is created), then
     * builds the response: the new account's overview plus branches/ATMs in the customer's state. The customer still needs a login
     * (see the customer credential calls) before they can use the customer portal.
     */
    public OpenAccountResponse openAccount(String employee, AccountRegistrationRequest request) {
        Account account = staffAccountService.openAccount(employee, request);
        return portalService.openAccountResponse(account, request.getState());
    }

    /** A customer's daily request limit and today's usage (needs MANAGE_CUSTOMER_LOGINS, checked by the banking service first). */
    public CustomerRateLimitView customerRateLimit(String employee, Long customerId) {
        return staffLoginService.customerRateLimit(employee, customerId);
    }

    /** Sets a customer's own daily request limit, or with null puts them back on the default (needs MANAGE_CUSTOMER_LOGINS, checked first). */
    public CustomerRateLimitView setCustomerRateLimit(String employee, Long customerId, SetRateLimitRequest request) {
        return staffLoginService.setCustomerRateLimit(employee, customerId, request);
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

    public void setEmployeePassword(String employee, String employeeNumber, AdminSetPasswordRequest request) {
        staffLoginService.setEmployeePassword(employee, employeeNumber, request);
    }
}
