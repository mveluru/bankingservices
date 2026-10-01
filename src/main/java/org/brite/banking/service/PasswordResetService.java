package org.brite.banking.service;

import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.LoginState;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.domain.StoredSecurityAnswer;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.CustomerCredentialRepository;
import org.brite.banking.repository.EmployeeCredentialRepository;
import org.brite.banking.repository.EmployeeRepository;
import org.brite.banking.repository.SecurityAnswerRepository;
import org.brite.banking.request.SecurityAnswerRequest;
import org.brite.banking.rules.CustomerLoginProperties;
import org.brite.banking.rules.EmployeeLoginProperties;
import org.brite.banking.rules.PasswordResetProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Forgotten-password reset for employees and customers using three security questions, plus setting those questions.
 * <ul>
 *   <li>A user picks 3 different questions from the {@link SecurityQuestion} catalog and answers them while logged in
 *       ({@link #setQuestions}, which needs the current password). Answers are normalised (trimmed, lower-cased,
 *       whitespace collapsed) and only a BCrypt hash is stored.</li>
 *   <li>To reset, the user asks for their questions ({@link #questionsFor}), then submits all three answers with the new
 *       8-digit password ({@link #reset}). An unknown username or a user with no questions gets plausible fake questions
 *       and the same failure as wrong answers, so neither can be told apart.</li>
 * </ul>
 * Answers like "first car" are guessable, so every wrong attempt is counted and {@code banking.password-reset.*} locks the
 * reset for that login. A reset is refused unless the login is ACTIVE (a lock that has expired counts as ACTIVE), so it can
 * never undo an administrator's INACTIVE/SUSPENDED/LOCKED.
 */
@Service
@Slf4j
public class PasswordResetService {
    private static final int SLOTS = 3;

    private final SecurityAnswerRepository answers;
    private final EmployeeRepository employees;
    private final EmployeeCredentialRepository employeeCredentials;
    private final CustomerCredentialRepository customerCredentials;
    private final PasswordEncoder encoder;
    private final PasswordResetProperties properties;
    private final LoginSupport employeeLogin;
    private final LoginSupport customerLogin;
    /** Matched against when there is nobody to check, so that path costs the same as a wrong answer. */
    private final String dummyHash;

    public PasswordResetService(SecurityAnswerRepository answers, EmployeeRepository employees,
                                EmployeeCredentialRepository employeeCredentials, CustomerCredentialRepository customerCredentials,
                                PasswordEncoder encoder, PasswordResetProperties properties,
                                EmployeeLoginProperties employeeLoginProperties, CustomerLoginProperties customerLoginProperties) {
        this.answers = answers;
        this.employees = employees;
        this.employeeCredentials = employeeCredentials;
        this.customerCredentials = customerCredentials;
        this.encoder = encoder;
        this.properties = properties;
        this.employeeLogin = new LoginSupport(encoder, employeeLoginProperties.getMaxFailedAttempts(), employeeLoginProperties.getLockoutMinutes(), "employee");
        this.customerLogin = new LoginSupport(encoder, customerLoginProperties.getMaxFailedAttempts(), customerLoginProperties.getLockoutMinutes(), "customer");
        this.dummyHash = encoder.encode("no such answer");
    }

    /** A login resolved to what both kinds have in common; {@code save} persists the (mutated) state. */
    private record Subject(CredentialOwnerType type, Long ownerId, LoginState state, Runnable save) {
    }

    /**
     * The three questions this user must answer to reset their password. Unknown usernames and users who never chose questions
     * get three plausible questions derived from the username, indistinguishable from real ones.
     */
    @Transactional(readOnly = true)
    public List<SecurityQuestionView> questionsFor(CredentialOwnerType type, String username) {
        Optional<List<StoredSecurityAnswer>> stored = byUsername(type, username)
                .map(subject -> answers.findByOwner(type, subject.ownerId()))
                .filter(list -> list.size() == SLOTS);
        if (stored.isPresent()) {
            return stored.get().stream().map(a -> SecurityQuestionView.of(a.getQuestion())).toList();
        }
        return decoyQuestions(username);
    }

    /**
     * Sets the user's three security questions and answers. The acting user must give their current password.
     *
     * @param ownerKey the employee number (employees) or the customer id as text (customers), taken from the verified token
     * @throws IllegalArgumentException (mapped to 400) unless there are exactly 3 different questions with 2-100 character answers
     * @throws InvalidCredentialsException (mapped to 401) if the current password is wrong or the login no longer exists
     * @throws EmployeeLockedException (mapped to 423) while the login is locked from wrong passwords
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmployeeLockedException.class})
    public List<SecurityQuestionView> setQuestions(CredentialOwnerType type, String ownerKey, String currentPassword,
                                                   List<SecurityAnswerRequest> requested) {
        List<SecurityAnswerRequest> valid = validateAnswers(requested);
        Subject subject = byOwnerKey(type, ownerKey).orElseThrow(() -> new InvalidCredentialsException(BankingMessages.INVALID_CREDENTIALS));
        loginSupport(type).checkPassword(subject.state(), subject.ownerId(), currentPassword == null ? "" : currentPassword, state -> subject.save().run());

        List<StoredSecurityAnswer> toStore = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            SecurityAnswerRequest a = valid.get(i);
            toStore.add(new StoredSecurityAnswer(i + 1, a.getQuestion(), encoder.encode(normalize(a.getAnswer()))));
        }
        answers.saveAll(type, subject.ownerId(), toStore);
        log.info(BankingMessages.LOG_SECURITY_ANSWERS_SET, type, subject.ownerId());
        return valid.stream().map(a -> SecurityQuestionView.of(a.getQuestion())).toList();
    }

    /**
     * Resets a forgotten password. All three answers must be right; the new password must be exactly 8 digits. Wrong answers are
     * counted and lock the reset after {@code maxFailedAttempts}. On success the failure counters and any login lock are cleared
     * and tokens issued before now stop working (the filters compare the token's issue time with {@code passwordChangedAt}).
     *
     * @throws IllegalArgumentException (mapped to 400) if the new password isn't exactly 8 digits
     * @throws InvalidCredentialsException (mapped to 401) for an unknown user, no questions set, or wrong answers (one message)
     * @throws EmployeeLockedException (mapped to 423) while the reset is locked
     * @throws LoginNotActiveException (mapped to 403) if the answers are right but the login isn't ACTIVE
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmployeeLockedException.class, LoginNotActiveException.class})
    public void reset(CredentialOwnerType type, String username, List<SecurityAnswerRequest> submitted, String newPassword) {
        String newHash = loginSupport(type).hashNewPassword(newPassword);

        Optional<Subject> found = byUsername(type, username);
        List<StoredSecurityAnswer> stored = found.map(s -> answers.findByOwner(type, s.ownerId())).orElse(List.of());
        if (found.isEmpty() || stored.size() != SLOTS) {
            for (int i = 0; i < SLOTS; i++) {
                encoder.matches("no such answer", dummyHash);
            }
            log.warn(BankingMessages.LOG_PASSWORD_RESET_UNKNOWN, type);
            throw new InvalidCredentialsException(BankingMessages.RESET_FAILED);
        }
        Subject subject = found.get();
        LoginState state = subject.state();
        LocalDateTime now = LocalDateTime.now();

        if (state.getResetLockedUntil() != null && state.getResetLockedUntil().isAfter(now)) {
            throw new EmployeeLockedException(String.format(BankingMessages.RESET_LOCKED, state.getResetLockedUntil()));
        }

        if (!allAnswersCorrect(stored, submitted)) {
            int failed = state.getResetFailedAttempts() + 1;
            if (failed >= properties.getMaxFailedAttempts()) {
                state.setResetLockedUntil(now.plusMinutes(properties.getLockoutMinutes()));
                state.setResetFailedAttempts(0);
                log.warn(BankingMessages.LOG_PASSWORD_RESET_LOCKED, type, subject.ownerId(), state.getResetLockedUntil(), failed);
            } else {
                state.setResetFailedAttempts(failed);
                log.warn(BankingMessages.LOG_PASSWORD_RESET_FAILED, type, subject.ownerId(), failed);
            }
            subject.save().run();
            throw new InvalidCredentialsException(BankingMessages.RESET_FAILED);
        }

        LoginStatus status = state.effectiveStatus(now);
        if (status != LoginStatus.ACTIVE) {
            throw new LoginNotActiveException(String.format(BankingMessages.RESET_LOGIN_NOT_ACTIVE, status));
        }
        state.setPasswordHash(newHash);
        state.setPasswordChangedAt(now);
        state.setFailedAttempts(0);
        state.setLockedUntil(null);
        if (state.getStatus() == LoginStatus.LOCKED) {          // an expired automatic lock: clear it with the reset
            state.setStatus(LoginStatus.ACTIVE);
            state.setStatusReason(null);
            state.setStatusChangedAt(now);
        }
        state.setResetFailedAttempts(0);
        state.setResetLockedUntil(null);
        subject.save().run();
        log.info(BankingMessages.LOG_PASSWORD_RESET, type, subject.ownerId());
    }

    private boolean allAnswersCorrect(List<StoredSecurityAnswer> stored, List<SecurityAnswerRequest> submitted) {
        Map<SecurityQuestion, String> byQuestion = new EnumMap<>(SecurityQuestion.class);
        boolean wellFormed = submitted != null && submitted.size() == SLOTS;
        if (wellFormed) {
            for (SecurityAnswerRequest a : submitted) {
                if (a == null || a.getQuestion() == null || a.getAnswer() == null || byQuestion.put(a.getQuestion(), normalize(a.getAnswer())) != null) {
                    wellFormed = false;
                }
            }
        }
        boolean allMatch = wellFormed;
        for (StoredSecurityAnswer s : stored) {                  // every answer is always checked, so timing doesn't show which one was wrong
            String given = byQuestion.get(s.getQuestion());
            boolean ok = encoder.matches(given == null ? "" : given, s.getAnswerHash()) && given != null;
            allMatch &= ok;
        }
        return allMatch;
    }

    private List<SecurityAnswerRequest> validateAnswers(List<SecurityAnswerRequest> requested) {
        if (requested == null || requested.size() != SLOTS) {
            throw new IllegalArgumentException(BankingMessages.SECURITY_ANSWERS_COUNT);
        }
        Set<SecurityQuestion> seen = new HashSet<>();
        for (SecurityAnswerRequest a : requested) {
            if (a == null || a.getQuestion() == null || a.getAnswer() == null) {
                throw new IllegalArgumentException(BankingMessages.SECURITY_ANSWER_INVALID);
            }
            int length = normalize(a.getAnswer()).length();
            if (length < 2 || length > 100) {
                throw new IllegalArgumentException(BankingMessages.SECURITY_ANSWER_INVALID);
            }
            if (!seen.add(a.getQuestion())) {
                throw new IllegalArgumentException(BankingMessages.SECURITY_QUESTIONS_DISTINCT);
            }
        }
        return requested;
    }

    /** Trimmed, lower-cased with whitespace collapsed, so "  Honda   Civic " matches "honda civic". */
    static String normalize(String answer) {
        return answer.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private List<SecurityQuestionView> decoyQuestions(String username) {
        SecurityQuestion[] all = SecurityQuestion.values();
        int hash = username == null ? 0 : LoginSupport.normalize(username) == null ? 0 : LoginSupport.normalize(username).hashCode();
        int start = Math.floorMod(hash, all.length);
        int step = Math.floorMod(hash / 31, 2) == 0 ? 1 : all.length - 1;     // 1 and n-1 are coprime with 6, so the three indexes differ
        List<SecurityQuestionView> views = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            views.add(SecurityQuestionView.of(all[Math.floorMod(start + i * step, all.length)]));
        }
        return views;
    }

    private LoginSupport loginSupport(CredentialOwnerType type) {
        return type == CredentialOwnerType.EMPLOYEE ? employeeLogin : customerLogin;
    }

    private Optional<Subject> byUsername(CredentialOwnerType type, String username) {
        String name = LoginSupport.normalize(username);
        if (name == null) {
            return Optional.empty();
        }
        return switch (type) {
            case EMPLOYEE -> employeeCredentials.findByUsername(name)
                    .map(c -> new Subject(type, c.getEmployeeId(), c, () -> employeeCredentials.save(c)));
            case CUSTOMER -> customerCredentials.findByUsername(name)
                    .map(c -> new Subject(type, c.getCustomerId(), c, () -> customerCredentials.save(c)));
        };
    }

    private Optional<Subject> byOwnerKey(CredentialOwnerType type, String ownerKey) {
        if (ownerKey == null) {
            return Optional.empty();
        }
        return switch (type) {
            case EMPLOYEE -> employees.findByEmployeeNumber(ownerKey)
                    .flatMap(e -> employeeCredentials.findByEmployeeId(e.getId()))
                    .map(c -> new Subject(type, c.getEmployeeId(), c, () -> employeeCredentials.save(c)));
            case CUSTOMER -> {
                try {
                    yield customerCredentials.findByCustomerId(Long.valueOf(ownerKey))
                            .map(c -> new Subject(type, c.getCustomerId(), c, () -> customerCredentials.save(c)));
                } catch (NumberFormatException e) {
                    yield Optional.empty();
                }
            }
        };
    }
}
