package org.brite.banking.service;

import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeCredential;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.EmployeeNotFoundException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.EmployeeCredentialRepository;
import org.brite.banking.repository.EmployeeRepository;
import org.brite.banking.rules.EmployeeLoginProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Creates and verifies employee logins: a username and an 8-digit password, stored only as a
 * BCrypt hash in its own table. This only checks credentials; the access token is issued by {@link LoginService}.
 */
@Service
@Slf4j
public class EmployeeCredentialService {
    private final EmployeeCredentialRepository credentialRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginSupport login;

    public EmployeeCredentialService(EmployeeCredentialRepository credentialRepository,
                                     EmployeeRepository employeeRepository,
                                     PasswordEncoder passwordEncoder,
                                     EmployeeLoginProperties properties) {
        this.credentialRepository = credentialRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.login = new LoginSupport(passwordEncoder, properties.getMaxFailedAttempts(), properties.getLockoutMinutes(), "employee");
    }

    /**
     * Gives an employee a login. The username is stored lowercase.
     *
     * @throws IllegalArgumentException (mapped to 400) for a bad username/password format, a taken
     *                                  username, or an employee who already has a login
     * @throws EmployeeNotFoundException (mapped to 404) if the employee doesn't exist
     */
    @Transactional
    public void createLogin(String employeeNumber, String username, String password) {
        String name = LoginSupport.normalize(username);
        String hash = login.hashNewLogin(name, password);
        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.EMPLOYEE_NOT_FOUND, employeeNumber)));
        if (credentialRepository.existsByEmployeeId(employee.getId())) {
            throw new IllegalArgumentException(String.format(BankingMessages.EMPLOYEE_HAS_LOGIN, employeeNumber));
        }
        if (credentialRepository.existsByUsername(name)) {
            throw new IllegalArgumentException(String.format(BankingMessages.EMPLOYEE_USERNAME_TAKEN, name));
        }
        credentialRepository.save(EmployeeCredential.builder()
                .employeeId(employee.getId())
                .username(name)
                .passwordHash(hash)
                .passwordChangedAt(LocalDateTime.now())
                .build());
        log.info(BankingMessages.LOG_LOGIN_CREATED, "employee", employeeNumber);
    }

    /**
     * Verifies a username and password and returns the employee. Unknown username and wrong password
     * give the same error. After {@code maxFailedAttempts} wrong passwords the login is locked for
     * {@code lockoutMinutes}; while locked the password isn't even checked. The failed-attempt count
     * is saved even though the call throws, hence {@code noRollbackFor}.
     *
     * @throws InvalidCredentialsException (mapped to 401) for an unknown username or wrong password
     * @throws EmployeeLockedException (mapped to 423) while the login is locked
     * @throws LoginNotActiveException (mapped to 403) if the password is right but the login is INACTIVE or SUSPENDED
     * @throws EmployeeNotAuthorizedException (mapped to 403) if the password is right but the employee isn't ACTIVE
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmployeeLockedException.class,
            EmployeeNotAuthorizedException.class, LoginNotActiveException.class})
    public Employee verify(String username, String password) {
        String name = LoginSupport.normalize(username);
        Optional<EmployeeCredential> found = name == null || password == null
                ? Optional.empty() : credentialRepository.findByUsername(name);
        if (found.isEmpty()) {
            throw login.unknownUser(password);
        }
        EmployeeCredential credential = found.get();
        login.checkPassword(credential, credential.getEmployeeId(), password,
                state -> credentialRepository.save(credential));

        Employee employee = employeeRepository.findById(credential.getEmployeeId())
                .orElseThrow(() -> new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS));
        if (credential.getStatus() != LoginStatus.ACTIVE) {
            throw new LoginNotActiveException(String.format(
                    BankingMessages.EMPLOYEE_LOGIN_NOT_ACTIVE, employee.getEmployeeNumber(), credential.getStatus()));
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new EmployeeNotAuthorizedException(String.format(
                    BankingMessages.EMPLOYEE_NOT_ACTIVE, employee.getEmployeeNumber(), employee.getStatus()));
        }
        login.recordSuccess(credential);
        credentialRepository.save(credential);
        log.info(BankingMessages.LOG_LOGIN_VERIFIED, "employee", employee.getEmployeeNumber());
        return employee;
    }

    /**
     * Sets the login status of an employee (ACTIVE, INACTIVE, LOCKED or SUSPENDED); only ACTIVE may transact.
     *
     * @throws EmployeeNotFoundException (mapped to 404) if there is no such employee or it has no login
     * @throws IllegalArgumentException (mapped to 400) for a missing status or an over-long reason
     */
    @Transactional
    public LoginStatusView changeStatus(String employeeNumber, LoginStatus status, String reason) {
        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.EMPLOYEE_NOT_FOUND, employeeNumber)));
        EmployeeCredential credential = credentialRepository.findByEmployeeId(employee.getId())
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.LOGIN_NOT_FOUND, "employee", employeeNumber)));
        login.applyStatus(credential, employee.getId(), status, reason);
        EmployeeCredential saved = credentialRepository.save(credential);
        return LoginStatusView.builder().username(saved.getUsername()).status(saved.getStatus())
                .statusReason(saved.getStatusReason()).statusChangedAt(saved.getStatusChangedAt())
                .lockedUntil(saved.getLockedUntil()).build();
    }

    /**
     * A logged-in employee changes their own password. The current password is checked like a login (wrong guesses count toward
     * the login lock), the new one must be exactly 8 digits and different, and the login must be ACTIVE. Every token issued before now
     * stops working, including the caller's own: they log in again with the new password.
     *
     * @throws IllegalArgumentException (mapped to 400) for a bad or unchanged new password
     * @throws InvalidCredentialsException (mapped to 401) if the current password is wrong or the login no longer exists
     * @throws EmployeeLockedException (mapped to 423) while the login is locked
     * @throws LoginNotActiveException (mapped to 403) if the login is INACTIVE or SUSPENDED
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmployeeLockedException.class, LoginNotActiveException.class})
    public void changePassword(String employeeNumber, String currentPassword, String newPassword) {
        String hash = login.hashNewPassword(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException(BankingMessages.PASSWORD_UNCHANGED);
        }
        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS));
        EmployeeCredential credential = credentialRepository.findByEmployeeId(employee.getId())
                .orElseThrow(() -> new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS));
        login.checkPassword(credential, employee.getId(), currentPassword == null ? "" : currentPassword, state -> credentialRepository.save(credential));
        if (credential.getStatus() != LoginStatus.ACTIVE) {
            throw new LoginNotActiveException(String.format(BankingMessages.EMPLOYEE_LOGIN_NOT_ACTIVE, employee.getEmployeeNumber(), credential.getStatus()));
        }
        credential.setPasswordHash(hash);
        credential.setPasswordChangedAt(LocalDateTime.now());
        credential.setFailedAttempts(0);
        credential.setResetFailedAttempts(0);
        credential.setResetLockedUntil(null);
        credentialRepository.save(credential);
        log.info(BankingMessages.LOG_PASSWORD_CHANGED, "employee", employee.getId());
    }

    /**
     * An administrator sets the employee's password to a new 8-digit value; the failure counters (login and password reset) are
     * cleared, the status is left as it is, and tokens issued before now stop working.
     *
     * @throws IllegalArgumentException (mapped to 400) if the password isn't exactly 8 digits
     * @throws EmployeeNotFoundException (mapped to 404) if there is no such employee or it has no login
     */
    @Transactional
    public void adminSetPassword(String employeeNumber, String newPassword) {
        String hash = login.hashNewPassword(newPassword);
        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.EMPLOYEE_NOT_FOUND, employeeNumber)));
        EmployeeCredential credential = credentialRepository.findByEmployeeId(employee.getId())
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.LOGIN_NOT_FOUND, "employee", employeeNumber)));
        credential.setPasswordHash(hash);
        credential.setPasswordChangedAt(LocalDateTime.now());
        credential.setFailedAttempts(0);
        credential.setResetFailedAttempts(0);
        credential.setResetLockedUntil(null);
        credentialRepository.save(credential);
        log.info(BankingMessages.LOG_PASSWORD_SET_BY_ADMIN, "employee", employee.getId());
    }

    /**
     * A token only proves who logged in at the time: one issued before the employee's password last changed (a reset) is refused.
     *
     * @throws InvalidTokenException (mapped to 401) if the employee or their login no longer exists, or the token predates the password
     */
    @Transactional(readOnly = true)
    public void requireTokenCurrent(String employeeNumber, Instant issuedAt) {
        EmployeeCredential credential = employeeRepository.findByEmployeeNumber(employeeNumber)
                .flatMap(e -> credentialRepository.findByEmployeeId(e.getId()))
                .orElseThrow(() -> new InvalidTokenException(BankingMessages.INVALID_TOKEN));
        if (LoginSupport.tokenPredatesPasswordChange(credential.getPasswordChangedAt(), issuedAt)) {
            throw new InvalidTokenException(BankingMessages.INVALID_TOKEN);
        }
    }
}
