package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.springframework.stereotype.Service;

/**
 * Account actions performed by bank employees: checks the acting employee's privilege first,
 * then delegates to the same services the customer endpoints use (so every business rule,
 * cache eviction and notification is unchanged). Nothing is called if the check fails.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StaffAccountService {
    private final EmployeeService employeeService;
    private final ClientAccountService clientAccountService;
    private final AccountSuspensionService accountSuspensionService;

    public Account withdraw(String employeeNumber, WithdrawalRequest request) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.WITHDRAW);
        Account account = clientAccountService.withdrawAndSaveToAccount(request);
        logAction(employee, EmployeePrivilege.WITHDRAW, request.getAccountNumber());
        return account;
    }

    public Account deposit(String employeeNumber, DepositForm request) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.DEPOSIT);
        Account account = clientAccountService.depositAndSaveToAccount(request);
        logAction(employee, EmployeePrivilege.DEPOSIT, request.getAccountNumber());
        return account;
    }

    public Account suspend(String employeeNumber, String accountNumber, SuspendAccountRequest request) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.SUSPEND_ACCOUNT);
        Account account = accountSuspensionService.suspendAccount(accountNumber, request);
        logAction(employee, EmployeePrivilege.SUSPEND_ACCOUNT, accountNumber);
        return account;
    }

    public Account updateSuspension(String employeeNumber, String accountNumber, UpdateSuspensionRequest request) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.UPDATE_SUSPENSION);
        Account account = accountSuspensionService.updateSuspension(accountNumber, request);
        logAction(employee, EmployeePrivilege.UPDATE_SUSPENSION, accountNumber);
        return account;
    }

    public Account reactivate(String employeeNumber, String accountNumber) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.REACTIVATE_ACCOUNT);
        Account account = accountSuspensionService.reactivateAccount(accountNumber);
        logAction(employee, EmployeePrivilege.REACTIVATE_ACCOUNT, accountNumber);
        return account;
    }

    public Account close(String employeeNumber, String accountNumber) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.CLOSE_ACCOUNT);
        Account account = clientAccountService.closeAccount(accountNumber);
        logAction(employee, EmployeePrivilege.CLOSE_ACCOUNT, accountNumber);
        return account;
    }

    private void logAction(Employee employee, EmployeePrivilege action, String accountNumber) {
        log.info(BankingMessages.LOG_STAFF_ACTION, employee.getEmployeeNumber(), employee.getRole(), action, accountNumber);
    }
}
