package org.brite.banking.service;

import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeCredential;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.EmployeeNotFoundException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.repository.EmployeeCredentialRepository;
import org.brite.banking.repository.EmployeeRepository;
import org.brite.banking.rules.EmployeeLoginProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeCredentialServiceTest {
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private EmployeeCredentialRepository credentials;
    private EmployeeRepository employees;
    private EmployeeCredentialService service;

    @BeforeEach
    void setUp() {
        credentials = mock(EmployeeCredentialRepository.class);
        employees = mock(EmployeeRepository.class);
        EmployeeLoginProperties properties = new EmployeeLoginProperties();
        properties.setMaxFailedAttempts(3);
        properties.setLockoutMinutes(15);
        service = new EmployeeCredentialService(credentials, employees, ENCODER, properties);
    }

    private Employee employee(EmployeeStatus status) {
        Employee e = Employee.builder().id(7L).employeeNumber("EMP-000010").role(EmployeeRole.TELLER).status(status).build();
        when(employees.findByEmployeeNumber("EMP-000010")).thenReturn(Optional.of(e));
        when(employees.findById(7L)).thenReturn(Optional.of(e));
        return e;
    }

    private EmployeeCredential stored(int failed, LocalDateTime lockedUntil) {
        EmployeeCredential c = EmployeeCredential.builder().employeeId(7L).username("lucas.meyer")
                .passwordHash(ENCODER.encode("20260010")).failedAttempts(failed).lockedUntil(lockedUntil)
                .status(lockedUntil == null ? LoginStatus.ACTIVE : LoginStatus.LOCKED).build();
        when(credentials.findByUsername("lucas.meyer")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void createLoginStoresOnlyABcryptHashAndLowercasesTheUsername() {
        employee(EmployeeStatus.ACTIVE);
        service.createLogin("EMP-000010", "Lucas.Meyer", "12345678");

        ArgumentCaptor<EmployeeCredential> captor = ArgumentCaptor.forClass(EmployeeCredential.class);
        verify(credentials).save(captor.capture());
        EmployeeCredential saved = captor.getValue();
        assertEquals("lucas.meyer", saved.getUsername());
        assertEquals(7L, saved.getEmployeeId());
        assertNotEquals("12345678", saved.getPasswordHash());
        assertTrue(ENCODER.matches("12345678", saved.getPasswordHash()));
    }

    @Test
    void createLoginRejectsPasswordsThatAreNotExactlyEightDigits() {
        employee(EmployeeStatus.ACTIVE);
        for (String bad : new String[]{"1234567", "123456789", "1234567a", "", " 1234567", null}) {
            assertThrows(IllegalArgumentException.class, () -> service.createLogin("EMP-000010", "lucas.meyer", bad), String.valueOf(bad));
        }
        verify(credentials, never()).save(any());
    }

    @Test
    void createLoginRejectsBadUsernamesDuplicatesAndUnknownEmployees() {
        employee(EmployeeStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> service.createLogin("EMP-000010", "ab", "12345678"));
        assertThrows(IllegalArgumentException.class, () -> service.createLogin("EMP-000010", "has space", "12345678"));

        when(credentials.existsByUsername("lucas.meyer")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createLogin("EMP-000010", "lucas.meyer", "12345678"));

        when(credentials.existsByUsername("lucas.meyer")).thenReturn(false);
        when(credentials.existsByEmployeeId(7L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createLogin("EMP-000010", "lucas.meyer", "12345678"));

        when(employees.findByEmployeeNumber("EMP-NONE")).thenReturn(Optional.empty());
        assertThrows(EmployeeNotFoundException.class, () -> service.createLogin("EMP-NONE", "someone", "12345678"));
        verify(credentials, never()).save(any());
    }

    @Test
    void verifyReturnsTheEmployeeAndClearsFailuresOnSuccess() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = stored(2, null);

        Employee result = service.verify("  Lucas.Meyer ", "20260010");

        assertEquals("EMP-000010", result.getEmployeeNumber());
        assertEquals(0, c.getFailedAttempts());
        assertNotNull(c.getLastLoginAt());
        verify(credentials).save(c);
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        employee(EmployeeStatus.ACTIVE);
        stored(0, null);
        when(credentials.findByUsername("nobody")).thenReturn(Optional.empty());

        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class, () -> service.verify("lucas.meyer", "00000001"));
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class, () -> service.verify("nobody", "20260010"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertThrows(InvalidCredentialsException.class, () -> service.verify(null, "20260010"));
        assertThrows(InvalidCredentialsException.class, () -> service.verify("lucas.meyer", null));
    }

    @Test
    void wrongPasswordCountsFailuresAndLocksAtTheLimit() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = stored(0, null);

        assertThrows(InvalidCredentialsException.class, () -> service.verify("lucas.meyer", "00000001"));
        assertEquals(1, c.getFailedAttempts());
        assertThrows(InvalidCredentialsException.class, () -> service.verify("lucas.meyer", "00000001"));
        assertEquals(2, c.getFailedAttempts());
        assertNull(c.getLockedUntil());

        assertThrows(InvalidCredentialsException.class, () -> service.verify("lucas.meyer", "00000001"));
        assertNotNull(c.getLockedUntil());
        assertTrue(c.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(14)));
        assertEquals(0, c.getFailedAttempts());
    }

    @Test
    void lockedLoginIsRefusedEvenWithTheRightPasswordAndDoesNotChangeTheLock() {
        employee(EmployeeStatus.ACTIVE);
        LocalDateTime until = LocalDateTime.now().plusMinutes(10);
        EmployeeCredential c = stored(0, until);

        assertThrows(EmployeeLockedException.class, () -> service.verify("lucas.meyer", "20260010"));
        assertEquals(until, c.getLockedUntil());
        verify(credentials, never()).save(any());
    }

    @Test
    void expiredLockNoLongerBlocksALogin() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = stored(0, LocalDateTime.now().minusMinutes(1));

        service.verify("lucas.meyer", "20260010");
        assertNull(c.getLockedUntil());
    }

    @Test
    void correctPasswordForAnInactiveEmployeeIsForbidden() {
        employee(EmployeeStatus.TERMINATED);
        stored(0, null);
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.verify("lucas.meyer", "20260010"));
    }

    @Test
    void exceptionsMapToUnauthorizedAndLocked() {
        BankingExceptionHandler handler = new BankingExceptionHandler();
        assertEquals(HttpStatus.UNAUTHORIZED, handler.handleInvalidCredentials(new InvalidCredentialsException("x")).getStatusCode());
        assertEquals(HttpStatus.LOCKED, handler.handleEmployeeLocked(new EmployeeLockedException("x")).getStatusCode());
    }

    private EmployeeCredential storedWith(LoginStatus status) {
        EmployeeCredential c = EmployeeCredential.builder().employeeId(7L).username("lucas.meyer")
                .passwordHash(ENCODER.encode("20260010")).status(status).build();
        when(credentials.findByUsername("lucas.meyer")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void lockingByFailuresSetsTheLockedStatusWithAReason() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = stored(0, null);
        for (int i = 0; i < 3; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.verify("lucas.meyer", "00000001"));
        }
        assertEquals(LoginStatus.LOCKED, c.getStatus());
        assertNotNull(c.getStatusReason());
    }

    @Test
    void rightPasswordOnAnInactiveOrSuspendedLoginIsForbidden() {
        employee(EmployeeStatus.ACTIVE);
        for (LoginStatus status : new LoginStatus[]{LoginStatus.INACTIVE, LoginStatus.SUSPENDED}) {
            storedWith(status);
            LoginNotActiveException ex = assertThrows(LoginNotActiveException.class, () -> service.verify("lucas.meyer", "20260010"));
            assertTrue(ex.getMessage().contains(status.name()));
        }
    }

    @Test
    void wrongPasswordsNeverOverwriteAnAdministratorsStatus() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = storedWith(LoginStatus.SUSPENDED);
        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.verify("lucas.meyer", "00000001"));
        }
        assertEquals(LoginStatus.SUSPENDED, c.getStatus());
        assertEquals(0, c.getFailedAttempts());
        verify(credentials, never()).save(any());
    }

    @Test
    void anAdministratorLockHasNoExpiryAndRefusesTheRightPassword() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = storedWith(LoginStatus.LOCKED);
        assertThrows(EmployeeLockedException.class, () -> service.verify("lucas.meyer", "20260010"));
        assertEquals(LoginStatus.LOCKED, c.getStatus());
    }

    @Test
    void anExpiredAutomaticLockBecomesActiveAgainOnLogin() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = stored(0, LocalDateTime.now().minusMinutes(1));
        service.verify("lucas.meyer", "20260010");
        assertEquals(LoginStatus.ACTIVE, c.getStatus());
        assertNull(c.getLockedUntil());
    }

    @Test
    void changeStatusSetsTheStatusAndReasonAndClearsLockAndFailures() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = stored(2, LocalDateTime.now().plusMinutes(5));
        when(credentials.findByEmployeeId(7L)).thenReturn(Optional.of(c));
        when(credentials.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginStatusView view = service.changeStatus("EMP-000010", LoginStatus.ACTIVE, "  Verified by phone ");

        assertEquals(LoginStatus.ACTIVE, view.getStatus());
        assertEquals("Verified by phone", view.getStatusReason());
        assertNull(view.getLockedUntil());
        assertEquals(0, c.getFailedAttempts());
        assertNotNull(c.getStatusChangedAt());
    }

    @Test
    void changeStatusValidatesInputAndFindsTheLogin() {
        employee(EmployeeStatus.ACTIVE);
        EmployeeCredential c = stored(0, null);
        when(credentials.findByEmployeeId(7L)).thenReturn(Optional.of(c));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus("EMP-000010", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus("EMP-000010", LoginStatus.SUSPENDED, "x".repeat(201)));
        when(credentials.findByEmployeeId(7L)).thenReturn(Optional.empty());
        assertThrows(EmployeeNotFoundException.class, () -> service.changeStatus("EMP-000010", LoginStatus.SUSPENDED, null));
    }
}
