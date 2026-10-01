package org.brite.banking.service;

import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CustomerCredential;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.CustomerNotFoundException;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.repository.CustomerCredentialRepository;
import org.brite.banking.repository.CustomerRepository;
import org.brite.banking.rules.CustomerLoginProperties;
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

class CustomerCredentialServiceTest {
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private CustomerCredentialRepository credentials;
    private CustomerRepository customers;
    private CustomerCredentialService service;

    @BeforeEach
    void setUp() {
        credentials = mock(CustomerCredentialRepository.class);
        customers = mock(CustomerRepository.class);
        CustomerLoginProperties properties = new CustomerLoginProperties();
        properties.setMaxFailedAttempts(3);
        service = new CustomerCredentialService(credentials, customers, ENCODER, properties);
        when(customers.findIdentityById(5L)).thenReturn(Optional.of(
                AuthenticatedCustomer.builder().customerId(5L).firstName("Alice").lastName("Smith").build()));
    }

    private CustomerCredential stored(int failed, LocalDateTime lockedUntil) {
        CustomerCredential c = CustomerCredential.builder().customerId(5L).username("customer0005")
                .passwordHash(ENCODER.encode("20260005")).failedAttempts(failed).lockedUntil(lockedUntil)
                .status(lockedUntil == null ? LoginStatus.ACTIVE : LoginStatus.LOCKED).build();
        when(credentials.findByUsername("customer0005")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void createLoginStoresOnlyABcryptHash() {
        when(credentials.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.createLogin(5L, "Alice.Smith", "12345678");

        ArgumentCaptor<CustomerCredential> captor = ArgumentCaptor.forClass(CustomerCredential.class);
        verify(credentials).save(captor.capture());
        assertEquals("alice.smith", captor.getValue().getUsername());
        assertNotEquals("12345678", captor.getValue().getPasswordHash());
        assertTrue(ENCODER.matches("12345678", captor.getValue().getPasswordHash()));
    }

    @Test
    void createLoginEnforcesTheEightDigitPasswordAndUsernameRules() {
        for (String bad : new String[]{"1234567", "123456789", "abcdefgh", null}) {
            assertThrows(IllegalArgumentException.class, () -> service.createLogin(5L, "alice.smith", bad));
        }
        assertThrows(IllegalArgumentException.class, () -> service.createLogin(5L, "a b", "12345678"));
        verify(credentials, never()).save(any());
    }

    @Test
    void createLoginRejectsDuplicatesAndUnknownCustomers() {
        when(credentials.existsByUsername("alice.smith")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createLogin(5L, "alice.smith", "12345678"));
        when(credentials.existsByUsername("alice.smith")).thenReturn(false);
        when(credentials.existsByCustomerId(5L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createLogin(5L, "alice.smith", "12345678"));
        assertThrows(CustomerNotFoundException.class, () -> service.createLogin(99L, "someone", "12345678"));
        verify(credentials, never()).save(any());
    }

    @Test
    void verifyReturnsOnlyIdAndNameAndClearsFailures() {
        CustomerCredential c = stored(2, null);
        AuthenticatedCustomer result = service.verify(" Customer0005 ", "20260005");
        assertEquals(5L, result.getCustomerId());
        assertEquals("Alice", result.getFirstName());
        assertEquals(0, c.getFailedAttempts());
        assertNotNull(c.getLastLoginAt());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        stored(0, null);
        when(credentials.findByUsername("nobody")).thenReturn(Optional.empty());
        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class, () -> service.verify("customer0005", "00000001"));
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class, () -> service.verify("nobody", "20260005"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void threeWrongPasswordsLockAndALockedLoginRefusesTheRightPassword() {
        CustomerCredential c = stored(0, null);
        for (int i = 0; i < 3; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.verify("customer0005", "00000001"));
        }
        assertNotNull(c.getLockedUntil());
        assertThrows(EmployeeLockedException.class, () -> service.verify("customer0005", "20260005"));
    }

    @Test
    void customerNotFoundMapsTo404() {
        assertEquals(HttpStatus.NOT_FOUND,
                new BankingExceptionHandler().handleCustomerNotFound(new CustomerNotFoundException("x")).getStatusCode());
    }

    private CustomerCredential storedWith(LoginStatus status, LocalDateTime lockedUntil) {
        CustomerCredential c = CustomerCredential.builder().customerId(5L).username("customer0005")
                .passwordHash(ENCODER.encode("20260005")).status(status).lockedUntil(lockedUntil).build();
        when(credentials.findByUsername("customer0005")).thenReturn(Optional.of(c));
        when(credentials.findByCustomerId(5L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void rightPasswordOnAnInactiveOrSuspendedLoginIsForbidden() {
        for (LoginStatus status : new LoginStatus[]{LoginStatus.INACTIVE, LoginStatus.SUSPENDED}) {
            storedWith(status, null);
            assertThrows(LoginNotActiveException.class, () -> service.verify("customer0005", "20260005"));
        }
    }

    @Test
    void requireActiveLoginIfPresentOnlyRejectsLoginsThatAreNotActive() {
        when(credentials.findByCustomerId(6L)).thenReturn(Optional.empty());
        service.requireActiveLoginIfPresent(6L);                                  // no login: not restricted

        storedWith(LoginStatus.ACTIVE, null);
        service.requireActiveLoginIfPresent(5L);

        for (LoginStatus status : new LoginStatus[]{LoginStatus.INACTIVE, LoginStatus.SUSPENDED}) {
            storedWith(status, null);
            assertThrows(LoginNotActiveException.class, () -> service.requireActiveLoginIfPresent(5L), status.name());
        }
        storedWith(LoginStatus.LOCKED, LocalDateTime.now().plusMinutes(5));
        assertThrows(LoginNotActiveException.class, () -> service.requireActiveLoginIfPresent(5L));

        storedWith(LoginStatus.LOCKED, LocalDateTime.now().minusMinutes(1));      // lock expired: counts as active
        service.requireActiveLoginIfPresent(5L);
    }

    @Test
    void changeStatusUpdatesTheLoginAndNeedsAnExistingCustomerAndLogin() {
        CustomerCredential c = storedWith(LoginStatus.ACTIVE, null);
        when(credentials.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginStatusView view = service.changeStatus(5L, LoginStatus.SUSPENDED, "Disputed activity");
        assertEquals(LoginStatus.SUSPENDED, view.getStatus());
        assertEquals("Disputed activity", c.getStatusReason());

        assertThrows(CustomerNotFoundException.class, () -> service.changeStatus(99L, LoginStatus.ACTIVE, null));
        when(customers.findIdentityById(8L)).thenReturn(Optional.of(AuthenticatedCustomer.builder().customerId(8L).build()));
        when(credentials.findByCustomerId(8L)).thenReturn(Optional.empty());
        assertThrows(CustomerNotFoundException.class, () -> service.changeStatus(8L, LoginStatus.ACTIVE, null));
    }

    @Test
    void createLoginReturnsTheNewLoginsUsernameAndActiveStatus() {
        when(credentials.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LoginStatusView view = service.createLogin(5L, "Alice.Smith", "12345678");
        assertEquals("alice.smith", view.getUsername());
        assertEquals(LoginStatus.ACTIVE, view.getStatus());
    }

    @Test
    void requireActiveLoginAcceptsOnlyAnExistingActiveLogin() {
        storedWith(LoginStatus.ACTIVE, null);
        service.requireActiveLogin(5L);

        for (LoginStatus status : new LoginStatus[]{LoginStatus.INACTIVE, LoginStatus.SUSPENDED}) {
            storedWith(status, null);
            assertThrows(LoginNotActiveException.class, () -> service.requireActiveLogin(5L), status.name());
        }
        storedWith(LoginStatus.LOCKED, LocalDateTime.now().plusMinutes(5));
        assertThrows(LoginNotActiveException.class, () -> service.requireActiveLogin(5L));
        storedWith(LoginStatus.LOCKED, LocalDateTime.now().minusMinutes(1));        // expired lock counts as active
        service.requireActiveLogin(5L);

        when(credentials.findByCustomerId(5L)).thenReturn(Optional.empty());        // login removed after the token was issued
        assertThrows(InvalidCredentialsException.class, () -> service.requireActiveLogin(5L));
    }

    @Test
    void aTokenIssuedBeforeThePasswordChangedIsRefusedButOneIssuedAfterIsFine() {
        CustomerCredential c = storedWith(LoginStatus.ACTIVE, null);
        c.setPasswordChangedAt(LocalDateTime.now());

        service.requireActiveLogin(5L, java.time.Instant.now().plusSeconds(5));
        assertThrows(org.brite.banking.exception.InvalidTokenException.class,
                () -> service.requireActiveLogin(5L, java.time.Instant.now().minusSeconds(3600)));
        service.requireActiveLogin(5L);                                              // no issue time given: only the status is checked
    }

    @Test
    void adminSetPasswordHashesItClearsCountersKeepsTheStatusAndValidates() {
        CustomerCredential c = storedWith(LoginStatus.SUSPENDED, null);
        c.setFailedAttempts(2);
        c.setResetFailedAttempts(2);
        c.setResetLockedUntil(LocalDateTime.now().plusMinutes(20));
        when(credentials.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adminSetPassword(5L, "24681357");

        assertTrue(ENCODER.matches("24681357", c.getPasswordHash()));
        assertEquals(0, c.getFailedAttempts());
        assertEquals(0, c.getResetFailedAttempts());
        assertNull(c.getResetLockedUntil());
        assertEquals(LoginStatus.SUSPENDED, c.getStatus(), "an administrator's status is not changed by a password reset");
        assertThrows(IllegalArgumentException.class, () -> service.adminSetPassword(5L, "123"));
        assertThrows(CustomerNotFoundException.class, () -> service.adminSetPassword(99L, "24681357"));
    }

    @Test
    void changePasswordChecksTheCurrentOneSetsTheNewHashAndMovesPasswordChangedAtForward() {
        CustomerCredential c = storedWith(LoginStatus.ACTIVE, null);
        c.setFailedAttempts(2);
        when(credentials.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        service.changePassword(5L, "20260005", "13572468");

        assertTrue(ENCODER.matches("13572468", c.getPasswordHash()));
        assertFalse(ENCODER.matches("20260005", c.getPasswordHash()));
        assertEquals(0, c.getFailedAttempts());
        assertTrue(c.getPasswordChangedAt().isAfter(before));
    }

    @Test
    void aWrongCurrentPasswordIsCountedTowardTheLoginLockAndNothingChanges() {
        CustomerCredential c = storedWith(LoginStatus.ACTIVE, null);
        String hash = c.getPasswordHash();
        for (int i = 0; i < 3; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.changePassword(5L, "00000000", "13572468"));
        }
        assertEquals(LoginStatus.LOCKED, c.getStatus());
        assertEquals(hash, c.getPasswordHash());
        assertThrows(EmployeeLockedException.class, () -> service.changePassword(5L, "20260005", "13572468"));
    }

    @Test
    void changePasswordRejectsABadOrUnchangedNewPasswordAndAnInactiveLogin() {
        storedWith(LoginStatus.ACTIVE, null);
        for (String bad : new String[]{"1234567", "123456789", "abcdefgh", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> service.changePassword(5L, "20260005", bad));
        }
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(5L, "20260005", "20260005"));

        for (LoginStatus status : new LoginStatus[]{LoginStatus.INACTIVE, LoginStatus.SUSPENDED}) {
            storedWith(status, null);
            assertThrows(LoginNotActiveException.class, () -> service.changePassword(5L, "20260005", "13572468"), status.name());
        }
        when(credentials.findByCustomerId(5L)).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> service.changePassword(5L, "20260005", "13572468"));
    }
}
