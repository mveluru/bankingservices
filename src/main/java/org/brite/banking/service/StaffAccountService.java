package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.BankLocations;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.TransactionHandler;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.request.AccountRegistrationRequest;
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
    private final LocationBasedOperationService locationService;

    /**
     * The transaction records the employee and the branch/ATM: {@code locationId} if given,
     * else the employee's own branch (none for an area manager who passes no location).
     *
     * @throws org.brite.banking.exception.LocationNotFoundException (mapped to 404) for an unknown location
     */
    public Account withdraw(String employeeNumber, Long locationId, WithdrawalRequest request) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.WITHDRAW);
        Account account = clientAccountService.withdrawAndSaveToAccount(request, handler(employee, locationId));
        logAction(employee, EmployeePrivilege.WITHDRAW, request.getAccountNumber());
        return account;
    }

    /** See {@link #withdraw} for how the location is chosen. */
    public Account deposit(String employeeNumber, Long locationId, DepositForm request) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.DEPOSIT);
        Account account = clientAccountService.depositAndSaveToAccount(request, handler(employee, locationId));
        logAction(employee, EmployeePrivilege.DEPOSIT, request.getAccountNumber());
        return account;
    }

    /**
     * Opens an account for a customer, for example one standing at the office: needs the {@code OPEN_ACCOUNT} privilege (every role has it) and
     * then goes through the same {@link ClientAccountService#registerNewClientAccount} as every registration, so age, validation, caching and
     * notifications are unchanged. The check runs before anything is created.
     */
    public Account openAccount(String employeeNumber, AccountRegistrationRequest request) {
        Employee employee = employeeService.requirePrivilege(employeeNumber, EmployeePrivilege.OPEN_ACCOUNT);
        Account account = clientAccountService.registerNewClientAccount(request);
        logAction(employee, EmployeePrivilege.OPEN_ACCOUNT,
                account.getCheckingAccountNumber() != null ? account.getCheckingAccountNumber() : account.getSavingAccountNumber());
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

    private TransactionHandler handler(Employee employee, Long locationId) {
        Long id = locationId != null ? locationId : employee.getBankLocationId();
        BankLocations location = id == null ? null : locationService.getLocation(id);
        return TransactionHandler.builder()
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .employeeRole(employee.getRole())
                .bankLocationId(location == null ? null : location.getId())
                .bankLocationName(location == null ? null : location.getName())
                .bankLocationType(location == null ? null : location.getLocationType())
                .bankLocationCity(location == null || location.getBankAddress() == null ? null : location.getBankAddress().getCity())
                .bankLocationState(location == null || location.getBankAddress() == null ? null : location.getBankAddress().getState())
                .build();
    }

    private void logAction(Employee employee, EmployeePrivilege action, String accountNumber) {
        log.info(BankingMessages.LOG_STAFF_ACTION, employee.getEmployeeNumber(), employee.getRole(), action, accountNumber);
    }
}
