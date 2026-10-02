package org.brite.banking.service;

import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.AccountHolderLoginBlockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.rules.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test: an account holder with no ACTIVE account can't sign in, and is told the status and to contact customer support. */
class LoginServiceAccountStatusTest {
    private CustomerCredentialService customers;
    private AccountRepository accounts;
    private CustomerQuotaService quota;
    private LoginService service;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerCredentialService.class);
        accounts = mock(AccountRepository.class);
        quota = mock(CustomerQuotaService.class);
        JwtProperties properties = new JwtProperties();
        properties.setSecret("login-status-test-secret-at-least-32-chars!");
        service = new LoginService(mock(EmployeeCredentialService.class), customers, new JwtService(properties), quota,
                mock(EmployeeQuotaService.class), accounts);
        when(customers.verify("customer0007", "20260007"))
                .thenReturn(AuthenticatedCustomer.builder().customerId(7L).firstName("Sam").lastName("Roe").build());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "CLOSED", "INACTIVE", "DORMANT"})
    void aHolderWhoseOnlyAccountIsNotActiveCannotSignInAndIsToldWhy(AccountStatus status) {
        when(accounts.findStatusesByCustomerId(7L)).thenReturn(List.of(status));

        AccountHolderLoginBlockedException e = assertThrows(AccountHolderLoginBlockedException.class,
                () -> service.customerLogin("customer0007", "20260007"));

        assertTrue(e.getMessage().contains(status.name()), e.getMessage());
        assertTrue(e.getMessage().contains("contact the customer support service"), e.getMessage());
        verifyNoInteractions(quota);   // a blocked sign-in is not counted as a login
    }

    @Test
    void whenEveryAccountIsBlockedAllTheDistinctStatusesAreNamedOnce() {
        when(accounts.findStatusesByCustomerId(7L)).thenReturn(List.of(AccountStatus.CLOSED, AccountStatus.DORMANT, AccountStatus.CLOSED));

        AccountHolderLoginBlockedException e = assertThrows(AccountHolderLoginBlockedException.class,
                () -> service.customerLogin("customer0007", "20260007"));

        assertEquals("Sign-in is not available: your account status is CLOSED, DORMANT. Please contact the customer support service.", e.getMessage());
    }

    @Test
    void oneActiveAccountIsEnoughToSignInEvenIfTheOtherIsBlocked() {
        when(accounts.findStatusesByCustomerId(7L)).thenReturn(List.of(AccountStatus.SUSPENDED, AccountStatus.ACTIVE));

        CustomerLoginResponse response = service.customerLogin("customer0007", "20260007");

        assertNotNull(response.getAccessToken());
        verify(quota).recordLogin(7L);
    }

    @Test
    void aCustomerWithNoAccountsIsNotBlocked() {
        when(accounts.findStatusesByCustomerId(7L)).thenReturn(List.of());

        assertNotNull(service.customerLogin("customer0007", "20260007").getAccessToken());
    }

    @Test
    void aWrongPasswordNeverReachesTheAccountStatusCheckSoTheStatusIsNotRevealed() {
        when(customers.verify("customer0007", "00000000")).thenThrow(new InvalidCredentialsException("Invalid username or password"));

        assertThrows(InvalidCredentialsException.class, () -> service.customerLogin("customer0007", "00000000"));
        verifyNoInteractions(accounts);
    }

    @Test
    void staffLoginIsNotAffectedByAnyAccountStatus() {
        EmployeeCredentialService employees = mock(EmployeeCredentialService.class);
        JwtProperties properties = new JwtProperties();
        properties.setSecret("login-status-test-secret-at-least-32-chars!");
        LoginService staffService = new LoginService(employees, customers, new JwtService(properties), quota,
                mock(EmployeeQuotaService.class), accounts);
        when(employees.verify("lucas.meyer", "20260010")).thenReturn(
                Employee.builder().employeeNumber("EMP-000010").role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).build());

        assertNotNull(staffService.staffLogin("lucas.meyer", "20260010").getAccessToken());
        verifyNoInteractions(accounts);
    }
}
