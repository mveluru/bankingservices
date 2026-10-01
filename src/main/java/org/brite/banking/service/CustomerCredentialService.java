package org.brite.banking.service;

import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CustomerCredential;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.CustomerNotFoundException;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.CustomerCredentialRepository;
import org.brite.banking.repository.CustomerRepository;
import org.brite.banking.rules.CustomerLoginProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Creates and verifies customer logins: a username and an 8-digit password, stored only as a
 * BCrypt hash in its own table. Same rules and lockout as the employee login ({@link LoginSupport}).
 * This only checks credentials; the access token is issued by {@link LoginService}.
 */
@Service
@Slf4j
public class CustomerCredentialService {
    private final CustomerCredentialRepository credentialRepository;
    private final CustomerRepository customerRepository;
    private final LoginSupport login;

    public CustomerCredentialService(CustomerCredentialRepository credentialRepository,
                                     CustomerRepository customerRepository,
                                     PasswordEncoder passwordEncoder,
                                     CustomerLoginProperties properties) {
        this.credentialRepository = credentialRepository;
        this.customerRepository = customerRepository;
        this.login = new LoginSupport(passwordEncoder, properties.getMaxFailedAttempts(), properties.getLockoutMinutes(), "customer");
    }

    /**
     * Gives a customer a login. The username is stored lowercase.
     *
     * @throws IllegalArgumentException (mapped to 400) for a bad username/password format, a taken
     *                                  username, or a customer who already has a login
     * @throws CustomerNotFoundException (mapped to 404) if the customer doesn't exist
     */
    @Transactional
    public LoginStatusView createLogin(Long customerId, String username, String password) {
        String name = LoginSupport.normalize(username);
        String hash = login.hashNewLogin(name, password);
        customerRepository.findIdentityById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.format(BankingMessages.CUSTOMER_NOT_FOUND, customerId)));
        if (credentialRepository.existsByCustomerId(customerId)) {
            throw new IllegalArgumentException(String.format(BankingMessages.CUSTOMER_HAS_LOGIN, customerId));
        }
        if (credentialRepository.existsByUsername(name)) {
            throw new IllegalArgumentException(String.format(BankingMessages.EMPLOYEE_USERNAME_TAKEN, name));
        }
        CustomerCredential saved = credentialRepository.save(CustomerCredential.builder()
                .customerId(customerId)
                .username(name)
                .passwordHash(hash)
                .passwordChangedAt(LocalDateTime.now())
                .build());
        log.info(BankingMessages.LOG_LOGIN_CREATED, "customer", customerId);
        return LoginStatusView.builder().username(saved.getUsername()).status(saved.getStatus()).build();
    }

    /**
     * Verifies a username and password and returns who the customer is (id and name only). Unknown
     * username and wrong password give the same error; after too many wrong passwords the login locks
     * (see {@code banking.customer-login.*}). The failed-attempt count is saved even though the call
     * throws, hence {@code noRollbackFor}.
     *
     * @throws InvalidCredentialsException (mapped to 401) for an unknown username or wrong password
     * @throws EmployeeLockedException (mapped to 423) while the login is locked
     * @throws LoginNotActiveException (mapped to 403) if the password is right but the login is INACTIVE or SUSPENDED
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmployeeLockedException.class, LoginNotActiveException.class})
    public AuthenticatedCustomer verify(String username, String password) {
        String name = LoginSupport.normalize(username);
        Optional<CustomerCredential> found = name == null || password == null
                ? Optional.empty() : credentialRepository.findByUsername(name);
        if (found.isEmpty()) {
            throw login.unknownUser(password);
        }
        CustomerCredential credential = found.get();
        login.checkPassword(credential, credential.getCustomerId(), password,
                state -> credentialRepository.save(credential));

        AuthenticatedCustomer customer = customerRepository.findIdentityById(credential.getCustomerId())
                .orElseThrow(() -> new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS));
        if (credential.getStatus() != LoginStatus.ACTIVE) {
            throw new LoginNotActiveException(String.format(BankingMessages.CUSTOMER_LOGIN_NOT_ACTIVE, credential.getStatus()));
        }
        login.recordSuccess(credential);
        credentialRepository.save(credential);
        log.info(BankingMessages.LOG_LOGIN_VERIFIED, "customer", customer.getCustomerId());
        return customer;
    }

    /**
     * The rule for customer-initiated transactions: a customer who has a login may transact only while it is
     * ACTIVE (a LOCKED login whose lock has expired counts as ACTIVE). A customer with no login at all isn't
     * restricted, because the customer endpoints don't require one yet.
     *
     * @throws LoginNotActiveException (mapped to 403) if the customer's login is INACTIVE, SUSPENDED or LOCKED
     */
    @Transactional(readOnly = true)
    public void requireActiveLoginIfPresent(Long customerId) {
        credentialRepository.findByCustomerId(customerId).ifPresent(credential -> {
            LoginStatus status = credential.effectiveStatus(LocalDateTime.now());
            if (status != LoginStatus.ACTIVE) {
                throw new LoginNotActiveException(String.format(BankingMessages.CUSTOMER_LOGIN_NOT_ACTIVE, status));
            }
        });
    }

    /**
     * Called on every request an authenticated customer makes: the token proves who logged in, but their login must
     * still exist and be ACTIVE right now (a LOCKED login whose lock has expired counts as ACTIVE).
     *
     * @throws LoginNotActiveException (mapped to 403) if the login is INACTIVE, SUSPENDED or LOCKED
     * @throws InvalidCredentialsException (mapped to 401) if the customer no longer has a login
     */
    @Transactional(readOnly = true)
    public void requireActiveLogin(Long customerId) {
        requireActiveLogin(customerId, null);
    }

    /**
     * As above, and additionally refuses a token issued before the customer's password last changed (a reset), when {@code issuedAt} is given.
     *
     * @throws InvalidTokenException (mapped to 401) if the token predates the password
     */
    @Transactional(readOnly = true)
    public void requireActiveLogin(Long customerId, Instant issuedAt) {
        CustomerCredential credential = credentialRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS));
        if (LoginSupport.tokenPredatesPasswordChange(credential.getPasswordChangedAt(), issuedAt)) {
            throw new InvalidTokenException(BankingMessages.INVALID_TOKEN);
        }
        LoginStatus status = credential.effectiveStatus(LocalDateTime.now());
        if (status != LoginStatus.ACTIVE) {
            throw new LoginNotActiveException(String.format(BankingMessages.CUSTOMER_LOGIN_NOT_ACTIVE, status));
        }
    }

    /**
     * Sets the login status of a customer (ACTIVE, INACTIVE, LOCKED or SUSPENDED); only ACTIVE may transact.
     *
     * @throws CustomerNotFoundException (mapped to 404) if there is no such customer or it has no login
     * @throws IllegalArgumentException (mapped to 400) for a missing status or an over-long reason
     */
    @Transactional
    public LoginStatusView changeStatus(Long customerId, LoginStatus status, String reason) {
        customerRepository.findIdentityById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.format(BankingMessages.CUSTOMER_NOT_FOUND, customerId)));
        CustomerCredential credential = credentialRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.format(BankingMessages.LOGIN_NOT_FOUND, "customer", customerId)));
        login.applyStatus(credential, customerId, status, reason);
        CustomerCredential saved = credentialRepository.save(credential);
        return LoginStatusView.builder().username(saved.getUsername()).status(saved.getStatus())
                .statusReason(saved.getStatusReason()).statusChangedAt(saved.getStatusChangedAt())
                .lockedUntil(saved.getLockedUntil()).build();
    }

    /**
     * A logged-in customer changes their own password. The current password is checked like a login (wrong guesses count toward
     * the login lock), the new one must be exactly 8 digits and different, and the login must be ACTIVE. Every token issued before now
     * stops working, including the caller's own: they log in again with the new password.
     *
     * @throws IllegalArgumentException (mapped to 400) for a bad or unchanged new password
     * @throws InvalidCredentialsException (mapped to 401) if the current password is wrong or the login no longer exists
     * @throws EmployeeLockedException (mapped to 423) while the login is locked
     * @throws LoginNotActiveException (mapped to 403) if the login is INACTIVE or SUSPENDED
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmployeeLockedException.class, LoginNotActiveException.class})
    public void changePassword(Long customerId, String currentPassword, String newPassword) {
        String hash = login.hashNewPassword(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException(BankingMessages.PASSWORD_UNCHANGED);
        }
        CustomerCredential credential = credentialRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS));
        login.checkPassword(credential, customerId, currentPassword == null ? "" : currentPassword, state -> credentialRepository.save(credential));
        if (credential.getStatus() != LoginStatus.ACTIVE) {
            throw new LoginNotActiveException(String.format(BankingMessages.CUSTOMER_LOGIN_NOT_ACTIVE, credential.getStatus()));
        }
        credential.setPasswordHash(hash);
        credential.setPasswordChangedAt(LocalDateTime.now());
        credential.setFailedAttempts(0);
        credential.setResetFailedAttempts(0);
        credential.setResetLockedUntil(null);
        credentialRepository.save(credential);
        log.info(BankingMessages.LOG_PASSWORD_CHANGED, "customer", customerId);
    }

    /**
     * An administrator sets the customer's password to a new 8-digit value; the failure counters (login and password reset) are
     * cleared, the status is left as it is, and tokens issued before now stop working.
     *
     * @throws IllegalArgumentException (mapped to 400) if the password isn't exactly 8 digits
     * @throws CustomerNotFoundException (mapped to 404) if there is no such customer or it has no login
     */
    @Transactional
    public void adminSetPassword(Long customerId, String newPassword) {
        String hash = login.hashNewPassword(newPassword);
        customerRepository.findIdentityById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.format(BankingMessages.CUSTOMER_NOT_FOUND, customerId)));
        CustomerCredential credential = credentialRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(String.format(BankingMessages.LOGIN_NOT_FOUND, "customer", customerId)));
        credential.setPasswordHash(hash);
        credential.setPasswordChangedAt(LocalDateTime.now());
        credential.setFailedAttempts(0);
        credential.setResetFailedAttempts(0);
        credential.setResetLockedUntil(null);
        credentialRepository.save(credential);
        log.info(BankingMessages.LOG_PASSWORD_SET_BY_ADMIN, "customer", customerId);
    }
}
