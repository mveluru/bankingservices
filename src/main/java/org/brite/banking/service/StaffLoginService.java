package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.LoginRequest;
import org.springframework.stereotype.Service;

/**
 * Lets an employee set the login status (ACTIVE, INACTIVE, LOCKED, SUSPENDED) of an employee or customer
 * login, after checking their privilege. Only an ACTIVE login may perform transactions.
 */
@Service
@RequiredArgsConstructor
public class StaffLoginService {
    private final EmployeeService employeeService;
    private final EmployeeCredentialService employeeCredentialService;
    private final CustomerCredentialService customerCredentialService;

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
}
