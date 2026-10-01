package org.brite.banking.service;

import org.brite.banking.domain.Account;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.request.AccountRegistrationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test: opening an account for a customer at the office checks the OPEN_ACCOUNT privilege before anything is created. */
class StaffAccountServiceTest {
    private EmployeeService employees;
    private ClientAccountService clientAccounts;
    private StaffAccountService service;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeService.class);
        clientAccounts = mock(ClientAccountService.class);
        service = new StaffAccountService(employees, clientAccounts, mock(AccountSuspensionService.class), mock(LocationBasedOperationService.class));
    }

    @Test
    void openAccountChecksThePrivilegeThenRegistersThroughTheSameServiceAsEveryRegistration() {
        AccountRegistrationRequest request = AccountRegistrationRequest.builder().state("TX").accountType("checking").build();
        Account created = Account.builder().checkingAccountNumber("CH-0000010096").build();
        when(employees.requirePrivilege("EMP-T", EmployeePrivilege.OPEN_ACCOUNT)).thenReturn(
                org.brite.banking.domain.Employee.builder().employeeNumber("EMP-T").role(EmployeeRole.TELLER).build());
        when(clientAccounts.registerNewClientAccount(request)).thenReturn(created);

        assertSame(created, service.openAccount("EMP-T", request));

        var order = inOrder(employees, clientAccounts);
        order.verify(employees).requirePrivilege("EMP-T", EmployeePrivilege.OPEN_ACCOUNT);
        order.verify(clientAccounts).registerNewClientAccount(request);
    }

    @Test
    void aRejectedEmployeeNeverCreatesAnAccount() {
        AccountRegistrationRequest request = AccountRegistrationRequest.builder().state("TX").build();
        doThrow(new EmployeeNotAuthorizedException("not authorized")).when(employees).requirePrivilege("EMP-X", EmployeePrivilege.OPEN_ACCOUNT);

        assertThrows(EmployeeNotAuthorizedException.class, () -> service.openAccount("EMP-X", request));
        verifyNoInteractions(clientAccounts);
    }

    @Test
    void everyRoleMayOpenAnAccountSoItIsAFrontDeskTask() {
        for (EmployeeRole role : EmployeeRole.values()) {
            assertTrue(role.hasPrivilege(EmployeePrivilege.OPEN_ACCOUNT), role.name());
        }
    }
}
