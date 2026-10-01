package org.brite.banking.repository;

import org.brite.banking.component.PasswordEncoderConfig;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.repository.jpa.CustomerCredentialJpaRepository;
import org.brite.banking.repository.jpa.EmployeeCredentialJpaRepository;
import org.brite.banking.repository.jpa.SecurityAnswerJpaRepository;
import org.brite.banking.request.SecurityAnswerRequest;
import org.brite.banking.rules.CustomerLoginProperties;
import org.brite.banking.rules.EmployeeLoginProperties;
import org.brite.banking.rules.PasswordResetProperties;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real seeders, repositories, BCrypt and services against embedded H2, outside a test transaction (so commits are real,
 * including the wrong-answer counters saved while the call throws). Each test uses a different seeded user.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = PasswordResetPersistenceTest.Cfg.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "banking.password-reset.max-failed-attempts=5"
})
class PasswordResetPersistenceTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = SecurityAnswerJpaRepository.class)
    @EnableConfigurationProperties({EmployeeLoginProperties.class, CustomerLoginProperties.class, PasswordResetProperties.class})
    @Import({PasswordResetService.class, EmployeeCredentialService.class, CustomerCredentialService.class, SecurityAnswerRepository.class,
            EmployeeCredentialRepository.class, CustomerCredentialRepository.class, EmployeeRepository.class, CustomerRepository.class,
            EmployeeCredentialSeeder.class, EmployeeDataSeeder.class, BankLocationDataSeeder.class,
            CustomerCredentialSeeder.class, AccountDataSeeder.class, PasswordEncoderConfig.class})
    static class Cfg {
    }

    @Autowired private PasswordResetService reset;
    @Autowired private CustomerCredentialService customerService;
    @Autowired private EmployeeCredentialService employeeService;
    @Autowired private SecurityAnswerJpaRepository answerRows;
    @Autowired private SecurityAnswerRepository answers;
    @Autowired private CustomerCredentialJpaRepository customerRows;
    @Autowired private EmployeeCredentialJpaRepository employeeRows;

    private static SecurityAnswerRequest a(SecurityQuestion q, String answer) {
        return new SecurityAnswerRequest(q, answer);
    }

    private static List<SecurityAnswerRequest> three(String car, String school, String teacher) {
        return List.of(a(SecurityQuestion.FIRST_CAR, car), a(SecurityQuestion.FIRST_SCHOOL, school), a(SecurityQuestion.FIRST_TEACHER, teacher));
    }

    private void setUp(CredentialOwnerType type, String ownerKey, String currentPassword) {
        reset.setQuestions(type, ownerKey, currentPassword, three("Honda Civic", "Oak Street Elementary", "Mrs Patel"));
    }

    @Test
    void aCustomerSetsQuestionsThenResetsAForgottenPasswordWithForgivingAnswers() {
        setUp(CredentialOwnerType.CUSTOMER, "4", "20260004");
        assertEquals(List.of(SecurityQuestion.FIRST_CAR, SecurityQuestion.FIRST_SCHOOL, SecurityQuestion.FIRST_TEACHER),
                reset.questionsFor(CredentialOwnerType.CUSTOMER, "Customer0004").stream().map(SecurityQuestionView::getQuestion).toList());

        reset.reset(CredentialOwnerType.CUSTOMER, "customer0004", three("  honda   CIVIC ", "oak street elementary", "MRS PATEL"), "24681357");

        assertEquals(4L, customerService.verify("customer0004", "24681357").getCustomerId());
        assertThrows(InvalidCredentialsException.class, () -> customerService.verify("customer0004", "20260004"));
    }

    @Test
    void anEmployeeCanDoTheSameAndTheAnswersAreStoredOnlyAsHashes() {
        setUp(CredentialOwnerType.EMPLOYEE, "EMP-000021", "20260021");

        for (var row : answerRows.findAll()) {
            assertTrue(row.getAnswerHash().startsWith("$2"), "stored answers must be BCrypt hashes");
            assertFalse(row.getAnswerHash().toLowerCase().contains("honda"));
        }
        reset.reset(CredentialOwnerType.EMPLOYEE, "chloe.dubois", three("Honda Civic", "Oak Street Elementary", "Mrs Patel"), "11223344");

        assertEquals("EMP-000021", employeeService.verify("chloe.dubois", "11223344").getEmployeeNumber());
    }

    @Test
    void settingQuestionsNeedsTheCurrentPasswordAndValidAnswers() {
        assertThrows(InvalidCredentialsException.class, () -> reset.setQuestions(CredentialOwnerType.CUSTOMER, "6", "00000000", three("a1", "b1", "c1")));
        assertThrows(IllegalArgumentException.class, () -> reset.setQuestions(CredentialOwnerType.CUSTOMER, "6", "20260006",
                List.of(a(SecurityQuestion.FIRST_CAR, "x1"), a(SecurityQuestion.FIRST_CAR, "y1"), a(SecurityQuestion.FIRST_TEACHER, "z1"))));
        assertThrows(IllegalArgumentException.class, () -> reset.setQuestions(CredentialOwnerType.CUSTOMER, "6", "20260006", three("x", "yy", "zz")));
        assertThrows(IllegalArgumentException.class, () -> reset.setQuestions(CredentialOwnerType.CUSTOMER, "6", "20260006", three("x".repeat(101), "yy", "zz")));
        assertThrows(IllegalArgumentException.class, () -> reset.setQuestions(CredentialOwnerType.CUSTOMER, "6", "20260006",
                List.of(a(SecurityQuestion.FIRST_CAR, "x1"), a(SecurityQuestion.FIRST_SCHOOL, "y1"))));
        assertTrue(answers.findByOwner(CredentialOwnerType.CUSTOMER, 6L).isEmpty(), "nothing is stored for a rejected request");
    }

    @Test
    void choosingNewQuestionsReplacesTheOldOnesInPlaceSoThereAreAlwaysThreeRows() {
        setUp(CredentialOwnerType.CUSTOMER, "7", "20260007");
        long rowsAfterFirst = answerRows.count();

        reset.setQuestions(CredentialOwnerType.CUSTOMER, "7", "20260007", List.of(a(SecurityQuestion.FIRST_PET, "Rex"),
                a(SecurityQuestion.BIRTH_CITY, "Austin"), a(SecurityQuestion.CHILDHOOD_FRIEND, "Sam")));

        assertEquals(rowsAfterFirst, answerRows.count());
        assertEquals(List.of(SecurityQuestion.FIRST_PET, SecurityQuestion.BIRTH_CITY, SecurityQuestion.CHILDHOOD_FRIEND),
                answers.findByOwner(CredentialOwnerType.CUSTOMER, 7L).stream().map(s -> s.getQuestion()).toList());
        reset.reset(CredentialOwnerType.CUSTOMER, "customer0007", List.of(a(SecurityQuestion.FIRST_PET, "rex"),
                a(SecurityQuestion.BIRTH_CITY, "austin"), a(SecurityQuestion.CHILDHOOD_FRIEND, "sam")), "55667788");
        assertEquals(7L, customerService.verify("customer0007", "55667788").getCustomerId());
    }

    @Test
    void wrongAnswersAreCountedAndFiveLockTheResetEvenForTheRightAnswers() {
        setUp(CredentialOwnerType.CUSTOMER, "8", "20260008");

        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0008", three("Ford", "Oak Street Elementary", "Mrs Patel"), "11112222"));
        }
        assertNotNull(customerRows.findByUsername("customer0008").orElseThrow().getResetLockedUntil());

        assertThrows(EmployeeLockedException.class,
                () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0008", three("Honda Civic", "Oak Street Elementary", "Mrs Patel"), "11112222"));
        assertEquals(8L, customerService.verify("customer0008", "20260008").getCustomerId(), "the old password still works");
    }

    @Test
    void aWrongOrMissingOrDuplicatedAnswerSetFailsAndOneRightAnswerIsNotEnough() {
        setUp(CredentialOwnerType.CUSTOMER, "9", "20260009");

        assertThrows(InvalidCredentialsException.class, () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0009",
                three("Honda Civic", "Oak Street Elementary", "Wrong"), "11112222"));
        assertThrows(InvalidCredentialsException.class, () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0009",
                List.of(a(SecurityQuestion.FIRST_CAR, "Honda Civic"), a(SecurityQuestion.FIRST_SCHOOL, "Oak Street Elementary"),
                        a(SecurityQuestion.FIRST_SCHOOL, "Oak Street Elementary")), "11112222"));
        assertThrows(InvalidCredentialsException.class, () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0009",
                List.of(a(SecurityQuestion.FIRST_CAR, "Honda Civic"), a(SecurityQuestion.FIRST_SCHOOL, "Oak Street Elementary"),
                        a(SecurityQuestion.BIRTH_CITY, "Austin")), "11112222"));
        assertThrows(InvalidCredentialsException.class, () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0009", null, "11112222"));
    }

    @Test
    void anUnknownUserAndAUserWithoutQuestionsFailExactlyLikeWrongAnswersAndGetDecoyQuestions() {
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> reset.reset(CredentialOwnerType.CUSTOMER, "nobody.here", three("a1", "b1", "c1"), "11112222"));
        InvalidCredentialsException noQuestions = assertThrows(InvalidCredentialsException.class,
                () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0010", three("a1", "b1", "c1"), "11112222"));
        setUp(CredentialOwnerType.CUSTOMER, "3", "20260003");
        InvalidCredentialsException wrong = assertThrows(InvalidCredentialsException.class,
                () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0003", three("a1", "b1", "c1"), "11112222"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertEquals(wrong.getMessage(), noQuestions.getMessage());

        List<SecurityQuestionView> decoy = reset.questionsFor(CredentialOwnerType.CUSTOMER, "nobody.here");
        assertEquals(3, decoy.size());
        assertEquals(3, decoy.stream().map(SecurityQuestionView::getQuestion).distinct().count());
        assertEquals(decoy.stream().map(SecurityQuestionView::getQuestion).toList(),
                reset.questionsFor(CredentialOwnerType.CUSTOMER, " Nobody.Here ").stream().map(SecurityQuestionView::getQuestion).toList(), "decoys are stable");
        assertEquals(3, reset.questionsFor(CredentialOwnerType.CUSTOMER, "customer0010").size());
    }

    @Test
    void aNewPasswordThatIsNotEightDigitsIs400AndChangesNothing() {
        setUp(CredentialOwnerType.CUSTOMER, "2", "20260002");
        for (String bad : new String[]{"1234567", "123456789", "abcdefgh", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0002",
                    three("Honda Civic", "Oak Street Elementary", "Mrs Patel"), bad));
        }
        assertEquals(2L, customerService.verify("customer0002", "20260002").getCustomerId());
    }

    @Test
    void aResetCannotUndoAnAdministratorsInactiveSuspendedOrLockedStatus() {
        setUp(CredentialOwnerType.CUSTOMER, "1", "20260001");
        for (LoginStatus status : new LoginStatus[]{LoginStatus.INACTIVE, LoginStatus.SUSPENDED, LoginStatus.LOCKED}) {
            customerService.changeStatus(1L, status, "test");
            LoginNotActiveException ex = assertThrows(LoginNotActiveException.class, () -> reset.reset(CredentialOwnerType.CUSTOMER, "customer0001",
                    three("Honda Civic", "Oak Street Elementary", "Mrs Patel"), "99887766"), status.name());
            assertTrue(ex.getMessage().contains(status.name()));
            assertEquals(status, customerRows.findByUsername("customer0001").orElseThrow().getStatus());
        }
        customerService.changeStatus(1L, LoginStatus.ACTIVE, "test done");
        assertEquals(1L, customerService.verify("customer0001", "20260001").getCustomerId());
    }

    @Test
    void aSuccessfulResetClearsTheLoginFailureCounterAndMovesPasswordChangedAtForward() {
        setUp(CredentialOwnerType.CUSTOMER, "5", "20260005");
        assertThrows(InvalidCredentialsException.class, () -> customerService.verify("customer0005", "00000000"));
        LocalDateTime before = customerRows.findByUsername("customer0005").orElseThrow().getPasswordChangedAt();
        assertEquals(1, customerRows.findByUsername("customer0005").orElseThrow().getFailedAttempts());

        reset.reset(CredentialOwnerType.CUSTOMER, "customer0005", three("Honda Civic", "Oak Street Elementary", "Mrs Patel"), "33445566");

        var row = customerRows.findByUsername("customer0005").orElseThrow();
        assertEquals(0, row.getFailedAttempts());
        assertEquals(0, row.getResetFailedAttempts());
        assertFalse(row.getPasswordChangedAt().isBefore(before));
    }

    @Test
    void anAdministratorCanSetAPasswordWithoutQuestionsAndItClearsTheResetLock() {
        employeeService.adminSetPassword("EMP-000019", "76543210");
        assertEquals("EMP-000019", employeeService.verify("layla.hassan", "76543210").getEmployeeNumber());
        assertThrows(IllegalArgumentException.class, () -> employeeService.adminSetPassword("EMP-000019", "123"));

        customerService.adminSetPassword(10L, "12348765");
        assertEquals(10L, customerService.verify("customer0010", "12348765").getCustomerId());
    }
}
