package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.CustomerRateLimitView;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.request.SetRateLimitRequest;
import org.springframework.stereotype.Service;

/**
 * Lets an employee set the login status (ACTIVE, INACTIVE, LOCKED, SUSPENDED) of an employee or customer
 * login, after checking their privilege. Only an ACTIVE login may perform transactions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StaffLoginService {
    private final EmployeeService employeeService;
    private final EmployeeCredentialService employeeCredentialService;
    private final CustomerCredentialService customerCredentialService;
    private final CustomerQuotaService customerQuotaService;
    private final EmployeeQuotaService employeeQuotaService;

    /** Needs {@code MANAGE_EMPLOYEES} (area managers). */
    public LoginStatusView changeEmployeeLoginStatus(String actingEmployee, String employeeNumber, ChangeLoginStatusRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_EMPLOYEES);
        return employeeCredentialService.changeStatus(employeeNumber, request.getStatus(), request.getReason());
    }

    /**
     * Needs {@code MANAGE_CUSTOMER_LOGINS} (managers and up). Gives a customer a login (username + 8-digit password).
     * Customers can't use the protected account and portal endpoints without one.
     */
    public LoginStatusView createCustomerLogin(String actingEmployee, Long customerId, LoginRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_CUSTOMER_LOGINS);
        return customerCredentialService.createLogin(customerId, request.getUsername(), request.getPassword());
    }

    /** Needs {@code MANAGE_EMPLOYEES} (area managers). Sets an employee's password (their tokens stop working). */
    public void setEmployeePassword(String actingEmployee, String employeeNumber, AdminSetPasswordRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_EMPLOYEES);
        employeeCredentialService.adminSetPassword(employeeNumber, request.getNewPassword());
    }

    /** Needs {@code MANAGE_CUSTOMER_LOGINS} (managers and up). Sets a customer's password (their tokens stop working). */
    public void setCustomerPassword(String actingEmployee, Long customerId, AdminSetPasswordRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_CUSTOMER_LOGINS);
        customerCredentialService.adminSetPassword(customerId, request.getNewPassword());
    }

    /** Needs {@code MANAGE_CUSTOMER_LOGINS} (managers and up). */
    public LoginStatusView changeCustomerLoginStatus(String actingEmployee, Long customerId, ChangeLoginStatusRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_CUSTOMER_LOGINS);
        return customerCredentialService.changeStatus(customerId, request.getStatus(), request.getReason());
    }

    /** Needs {@code MANAGE_CUSTOMER_LOGINS} (managers and up). A customer's daily request limit and today's usage. */
    public CustomerRateLimitView customerRateLimit(String actingEmployee, Long customerId) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_CUSTOMER_LOGINS);
        return customerQuotaService.view(customerId);
    }

    /**
     * Needs {@code MANAGE_CUSTOMER_LOGINS} (managers and up). Gives a customer their own daily request limit, or with a null limit puts them
     * back on the default ({@code banking.rate-limit.requests-per-day}).
     */
    public CustomerRateLimitView setCustomerRateLimit(String actingEmployee, Long customerId, SetRateLimitRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_CUSTOMER_LOGINS);
        CustomerRateLimitView view = customerQuotaService.setLimit(customerId, request.getMaxRequestsPerDay());
        log.info(BankingMessages.LOG_RATE_LIMIT_CHANGED, actingEmployee, customerId, request.getMaxRequestsPerDay() != null ? request.getMaxRequestsPerDay() : "the default");
        return view;
    }

    /** Needs {@code MANAGE_EMPLOYEES} (area managers). An employee's daily request limit and today's usage. */
    public EmployeeRateLimitView employeeRateLimit(String actingEmployee, String employeeNumber) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_EMPLOYEES);
        return employeeQuotaService.view(employeeNumber);
    }

    /**
     * Needs {@code MANAGE_EMPLOYEES} (area managers). Gives an employee their own daily request limit, or with a null limit puts them back on
     * the default ({@code banking.rate-limit.employee-requests-per-day}).
     */
    public EmployeeRateLimitView setEmployeeRateLimit(String actingEmployee, String employeeNumber, SetRateLimitRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_EMPLOYEES);
        EmployeeRateLimitView view = employeeQuotaService.setLimit(employeeNumber, request.getMaxRequestsPerDay());
        log.info(BankingMessages.LOG_EMPLOYEE_RATE_LIMIT_CHANGED, actingEmployee, employeeNumber,
                request.getMaxRequestsPerDay() != null ? request.getMaxRequestsPerDay() : "the default");
        return view;
    }

    /** The acting employee's own daily request limit and today's usage; needs no privilege, only an ACTIVE employee and login. */
    public EmployeeRateLimitView ownRateLimit(String actingEmployee) {
        // an employee can always read their own profile, which also demands an ACTIVE employee with an ACTIVE login
        return employeeQuotaService.view(employeeService.getEmployee(actingEmployee, actingEmployee).getEmployeeNumber());
    }
}
