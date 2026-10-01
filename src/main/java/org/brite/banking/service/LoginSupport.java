package org.brite.banking.service;

import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.LoginState;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.messages.BankingMessages;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The username/password rules and lockout logic shared by the employee and customer logins, so
 * both behave identically: lowercase usernames, exactly-8-digit passwords, BCrypt hashes, one
 * error for unknown user and wrong password, and a lock after too many wrong passwords.
 */
@Slf4j
final class LoginSupport {
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,50}$");
    private static final Pattern PASSWORD = Pattern.compile("^\\d{8}$");

    private final PasswordEncoder encoder;
    private final int maxFailedAttempts;
    private final int lockoutMinutes;
    private final String subject;
    /** Matched against when the username is unknown, so that path costs the same as a wrong password. */
    private final String dummyHash;

    /** @param subject "employee" or "customer", used in log lines */
    LoginSupport(PasswordEncoder encoder, int maxFailedAttempts, int lockoutMinutes, String subject) {
        this.encoder = encoder;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
        this.subject = subject;
        this.dummyHash = encoder.encode("00000000");
    }

    /** Lowercased, trimmed username, or null if blank. */
    static String normalize(String username) {
        return username == null || username.isBlank() ? null : username.trim().toLowerCase();
    }

    /** @throws IllegalArgumentException (mapped to 400) for a bad username or password format */
    String hashNewLogin(String normalizedUsername, String password) {
        if (normalizedUsername == null || !USERNAME.matcher(normalizedUsername).matches()) {
            throw new IllegalArgumentException(BankingMessages.EMPLOYEE_USERNAME_INVALID);
        }
        if (password == null || !PASSWORD.matcher(password).matches()) {
            throw new IllegalArgumentException(BankingMessages.EMPLOYEE_PASSWORD_INVALID);
        }
        return encoder.encode(password);
    }

    /** Costs the same as a wrong password, then rejects. */
    InvalidCredentialsException unknownUser(String password) {
        encoder.matches(password == null ? "" : password, dummyHash);
        log.warn(BankingMessages.LOG_LOGIN_UNKNOWN_USER, subject);
        return new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS);
    }

    /**
     * Returns normally only if the login isn't locked and the password matches. Otherwise it throws,
     * saving the failure first (via {@code persist}) when the login is ACTIVE.
     * <ul>
     *   <li>A {@code LOCKED} login whose {@code lockedUntil} has passed is unlocked first (back to ACTIVE).</li>
     *   <li>A login still locked is refused without checking the password.</li>
     *   <li>A wrong password on an ACTIVE login counts a failure and, at the limit, sets {@code LOCKED} until
     *       {@code now + lockoutMinutes}. A wrong password on an INACTIVE/SUSPENDED login changes nothing, so
     *       a guess can never overwrite an administrator's status.</li>
     * </ul>
     *
     * @throws EmployeeLockedException (mapped to 423) while locked
     * @throws InvalidCredentialsException (mapped to 401) on a wrong password
     */
    void checkPassword(LoginState state, Long subjectId, String password, Consumer<LoginState> persist) {
        LocalDateTime now = LocalDateTime.now();
        if (state.getStatus() == LoginStatus.LOCKED) {
            if (state.getLockedUntil() != null && !state.getLockedUntil().isAfter(now)) {
                state.setStatus(LoginStatus.ACTIVE);
                state.setLockedUntil(null);
                state.setFailedAttempts(0);
                state.setStatusReason(null);
                state.setStatusChangedAt(now);
                log.info(BankingMessages.LOG_LOGIN_UNLOCKED, subject, subjectId);
            } else {
                log.warn(BankingMessages.LOG_LOGIN_REFUSED_LOCKED, subject, subjectId, state.getLockedUntil());
                throw new EmployeeLockedException(String.format(BankingMessages.EMPLOYEE_LOCKED,
                        state.getLockedUntil() == null ? "an administrator unlocks it" : state.getLockedUntil()));
            }
        }
        if (encoder.matches(password, state.getPasswordHash())) {
            return;
        }
        if (state.getStatus() == LoginStatus.ACTIVE) {
            int failed = state.getFailedAttempts() + 1;
            if (failed >= maxFailedAttempts) {
                state.setStatus(LoginStatus.LOCKED);
                state.setLockedUntil(now.plusMinutes(lockoutMinutes));
                state.setStatusReason("Too many failed login attempts");
                state.setStatusChangedAt(now);
                state.setFailedAttempts(0);
                log.warn(BankingMessages.LOG_LOGIN_LOCKED, subject, subjectId, state.getLockedUntil(), failed);
            } else {
                state.setFailedAttempts(failed);
                log.warn(BankingMessages.LOG_LOGIN_FAILED, subject, subjectId, failed);
            }
            persist.accept(state);
        }
        throw new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS);
    }

    void recordSuccess(LoginState state) {
        state.setFailedAttempts(0);
        state.setLockedUntil(null);
        state.setLastLoginAt(LocalDateTime.now());
    }

    /**
     * Administrator sets the status. {@code ACTIVE} also clears the failure count and any lock; the other
     * statuses are indefinite (no {@code lockedUntil}), so even {@code LOCKED} needs an administrator to lift it.
     *
     * @throws IllegalArgumentException (mapped to 400) if the status is null or the reason is over 200 characters
     */
    void applyStatus(LoginState state, Long subjectId, LoginStatus status, String reason) {
        if (status == null) {
            throw new IllegalArgumentException(BankingMessages.LOGIN_STATUS_REQUIRED);
        }
        if (reason != null && reason.length() > 200) {
            throw new IllegalArgumentException(BankingMessages.LOGIN_STATUS_REASON_TOO_LONG);
        }
        state.setStatus(status);
        state.setLockedUntil(null);
        state.setFailedAttempts(0);
        state.setStatusReason(reason == null || reason.isBlank() ? null : reason.trim());
        state.setStatusChangedAt(LocalDateTime.now());
        log.info(BankingMessages.LOG_LOGIN_STATUS_CHANGED, subject, subjectId, status, state.getStatusReason() == null ? "none" : "given");
    }
}
