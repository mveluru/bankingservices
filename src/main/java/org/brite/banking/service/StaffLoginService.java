package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.request.ChangeLoginStatusRequest;
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

    /** Needs {@code MANAGE_CUSTOMER_LOGINS} (managers and up). */
    public LoginStatusView changeCustomerLoginStatus(String actingEmployee, Long customerId, ChangeLoginStatusRequest request) {
        employeeService.requirePrivilege(actingEmployee, EmployeePrivilege.MANAGE_CUSTOMER_LOGINS);
        return customerCredentialService.changeStatus(customerId, request.getStatus(), request.getReason());
    }
}
