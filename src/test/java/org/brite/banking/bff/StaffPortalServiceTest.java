package org.brite.banking.bff;

import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.dto.PortalEmployee;
import org.brite.banking.bff.service.PortalOrchestrationService;
import org.brite.banking.bff.service.StaffPortalService;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.AccountSuspendedException;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.StaffAccountService;
import org.brite.banking.service.StaffLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test: the staff BFF only delegates to the banking staff services and rebuilds the overview afterwards. */
class StaffPortalServiceTest {
    private StaffAccountService accounts;
    private StaffLoginService logins;
    private EmployeeService employees;
    private PortalOrchestrationService portal;
    private StaffPortalService service;

    private static AccountOverviewResponse overviewOf(String number) {
        return new AccountOverviewResponse(number, AccountType.CHECKING, AccountStatus.ACTIVE, new BigDecimal("75.00"), false, null,
                LocalDate.of(2026, 1, 5), null, "Ada", "Lovelace", "***-***-0101", 30, List.of());
    }

    @BeforeEach
    void setUp() {
        accounts = mock(StaffAccountService.class);
        logins = mock(StaffLoginService.class);
        employees = mock(EmployeeService.class);
        portal = mock(PortalOrchestrationService.class);
        service = new StaffPortalService(accounts, logins, employees, portal);
        when(portal.overview("CH-1", null)).thenReturn(overviewOf("CH-1"));
    }

    @Test
    void everyAccountActionDelegatesWithTheEmployeeThenReturnsTheRefreshedOverview() {
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().AccountNumber("CH-1").withdrawAmount(BigDecimal.TEN).build();
        DepositForm deposit = new DepositForm();
        deposit.setAccountNumber("CH-1");
        SuspendAccountRequest suspend = SuspendAccountRequest.builder().notes("n").build();

        assertEquals("CH-1", service.withdraw("EMP-T", 1L, withdrawal).accountNumber());
        assertEquals("CH-1", service.deposit("EMP-T", null, deposit).accountNumber());
        assertEquals("CH-1", service.suspend("EMP-M", "CH-1", suspend).accountNumber());
        assertEquals("CH-1", service.reactivate("EMP-M", "CH-1").accountNumber());
        assertEquals("CH-1", service.close("EMP-M", "CH-1").accountNumber());

        verify(accounts).withdraw("EMP-T", 1L, withdrawal);
        verify(accounts).deposit("EMP-T", null, deposit);
        verify(accounts).suspend("EMP-M", "CH-1", suspend);
        verify(accounts).reactivate("EMP-M", "CH-1");
        verify(accounts).close("EMP-M", "CH-1");
    }

    @Test
    void theBankingActionRunsBeforeTheOverviewIsBuilt() {
        service.close("EMP-M", "CH-1");
        var order = inOrder(accounts, portal);
        order.verify(accounts).close("EMP-M", "CH-1");
        order.verify(portal).overview("CH-1", null);
    }

    @Test
    void aRejectedActionPropagatesAndNoOverviewIsBuilt() {
        doThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires CLOSE_ACCOUNT")).when(accounts).close("EMP-T", "CH-1");
        doThrow(new AccountSuspendedException("Account CH-1 is already suspended")).when(accounts).suspend(any(), any(), any());

        assertThrows(EmployeeNotAuthorizedException.class, () -> service.close("EMP-T", "CH-1"));
        assertThrows(AccountSuspendedException.class, () -> service.suspend("EMP-M", "CH-1", SuspendAccountRequest.builder().notes("n").build()));
        verifyNoInteractions(portal);
    }

    @Test
    void anOverviewForStaffNeedsViewAccountAndIsOtherwiseTheSameOverview() {
        when(portal.overview("CH-1", 7)).thenReturn(overviewOf("CH-1"));

        assertEquals("CH-1", service.overview("EMP-T", "CH-1", 7).accountNumber());
        verify(employees).requirePrivilege("EMP-T", EmployeePrivilege.VIEW_ACCOUNT);

        doThrow(new EmployeeNotAuthorizedException("not authorized")).when(employees).requirePrivilege("EMP-X", EmployeePrivilege.VIEW_ACCOUNT);
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.overview("EMP-X", "CH-1", 7));
    }

    @Test
    void employeesAreShownAsCardsWithoutEmailPhoneOrInternalIds() {
        Employee e = Employee.builder().id(10L).employeeNumber("EMP-000010").firstName("Lucas").lastName("Meyer").email("lucas.meyer@brite-bank.example")
                .phoneNumber("512-555-0210").role(EmployeeRole.TELLER).jobTitle("Senior Teller").status(EmployeeStatus.ACTIVE)
                .hireDate(LocalDate.of(2024, 7, 30)).bankLocationId(1L).supervisorId(4L).build();
        when(employees.listEmployees("EMP-A", EmployeeRole.TELLER, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(e)));
        when(employees.getEmployee("EMP-A", "EMP-000010")).thenReturn(e);

        PortalEmployee card = service.employees("EMP-A", EmployeeRole.TELLER, PageRequest.of(0, 20)).getContent().get(0);
        assertEquals("EMP-000010", card.employeeNumber());
        assertEquals(EmployeeRole.TELLER, card.role());
        assertEquals(1L, card.bankLocationId());
        assertTrue(card.privileges().contains(EmployeePrivilege.DEPOSIT));
        assertFalse(card.privileges().contains(EmployeePrivilege.SUSPEND_ACCOUNT));
        assertFalse(card.toString().contains("brite-bank.example") || card.toString().contains("555-0210"), "no email or phone on the card");
        assertEquals("Lucas", service.employee("EMP-A", "EMP-000010").firstName());
    }
}
