package org.bee.banking.repository;

import org.bee.banking.domain.AccountStatus;
import org.bee.banking.entity.AccountEntity;
import org.bee.banking.repository.jpa.AccountJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs both real seeders against embedded H2 (no MySQL). Minimal context: entities + account repositories only.
 * On the empty database the base seeder's 52 accounts come first (via {@code @DependsOn}), then the 40 new ones.
 */
@DataJpaTest
@ContextConfiguration(classes = AccountStatusDemoSeederTest.Cfg.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountStatusDemoSeederTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.bee.banking.entity")
    @EnableJpaRepositories(basePackageClasses = AccountJpaRepository.class)
    @Import({AccountDataSeeder.class, AccountStatusDemoSeeder.class})
    static class Cfg {
    }

    @Autowired
    private AccountJpaRepository repository;

    @Autowired
    private AccountStatusDemoSeeder seeder;

    private List<AccountEntity> byStatus(AccountStatus status) {
        return repository.findAll().stream().filter(a -> a.getAccountStatus() == status).toList();
    }

    @Test
    void baseAccountsAreSeededFirstAndThenTwentyClosedAndTwentySuspendedAreAdded() {
        assertEquals(92, repository.count());
        assertEquals(22, byStatus(AccountStatus.CLOSED).size());      // 2 from the base seeder + 20 new
        assertEquals(20, byStatus(AccountStatus.SUSPENDED).size());
        assertEquals(50, byStatus(AccountStatus.ACTIVE).size());
    }

    @Test
    void suspendedAccountsCarryFlagStartNotesAndAreNotYetExpired() {
        List<AccountEntity> suspended = byStatus(AccountStatus.SUSPENDED);
        LocalDateTime now = LocalDateTime.now();

        for (AccountEntity a : suspended) {
            assertTrue(a.isSuspended(), a.getAccountNumber());
            assertNotNull(a.getSuspendedStart(), a.getAccountNumber());
            assertTrue(a.getSuspendedStart().isBefore(now), a.getAccountNumber());
            assertTrue(a.getSuspensionNotes() != null && !a.getSuspensionNotes().isBlank(), a.getAccountNumber());
            assertNull(a.getClosedDate(), a.getAccountNumber());
            if (a.getSuspendedEnd() != null) {
                assertTrue(a.getSuspendedEnd().isAfter(now), a.getAccountNumber() + " must not already be expired");
            }
        }
        assertEquals(6, suspended.stream().filter(a -> a.getSuspendedEnd() == null).count());
        assertEquals(10, suspended.stream().filter(a -> a.getAccountNumber().startsWith("CH-")).count());
        assertEquals(10, suspended.stream().filter(a -> a.getAccountNumber().startsWith("SV-")).count());
    }

    @Test
    void newClosedAccountsHaveAClosedDateAfterCreationAndNoSuspension() {
        List<AccountEntity> closed = byStatus(AccountStatus.CLOSED).stream()
                .filter(a -> a.getAccountNumber().matches("(CH-00000300|SV-00000400)\\d\\d")).toList();

        assertEquals(20, closed.size());
        for (AccountEntity a : closed) {
            assertNotNull(a.getClosedDate(), a.getAccountNumber());
            assertTrue(a.getClosedDate().isAfter(a.getCreatedDate()), a.getAccountNumber());
            assertTrue(a.getClosedDate().isBefore(LocalDate.now()), a.getAccountNumber());
            assertTrue(!a.isSuspended() && a.getSuspendedStart() == null && a.getSuspensionNotes() == null, a.getAccountNumber());
        }
    }

    @Test
    void seededNumbersKeepThePrefixAndTenDigitFormatAndEveryAccountHasACustomer() {
        for (AccountEntity a : repository.findAll()) {
            assertTrue(a.getAccountNumber().matches("(CH|SV)-\\d{10}"), a.getAccountNumber());
            assertNotNull(a.getCustomer(), a.getAccountNumber());
        }
    }

    @Test
    void everySeededCustomerHasAUniqueWellFormedPhoneNumber() {
        List<String> phones = repository.findAll().stream().map(a -> a.getCustomer().getPhoneNumber()).toList();

        assertEquals(92, phones.size());
        for (String phone : phones) {
            assertNotNull(phone);
            assertTrue(phone.matches("\\d{3}-\\d{3}-\\d{4}"), phone);
        }
        assertEquals(92, phones.stream().distinct().count());
        assertEquals("512-555-0001", repository.findByAccountNumber("CH-0000088291").orElseThrow().getCustomer().getPhoneNumber());
        assertEquals("512-555-0053", repository.findByAccountNumber("CH-0000030001").orElseThrow().getCustomer().getPhoneNumber());
        assertEquals("512-555-0092", repository.findByAccountNumber("SV-0000060010").orElseThrow().getCustomer().getPhoneNumber());
    }

    @Test
    void rerunningNeverDuplicatesAndRestoresAMissingAccount() {
        seeder.seedMissing();
        assertEquals(92, repository.count());

        repository.delete(repository.findByAccountNumber("SV-0000060010").orElseThrow());
        assertEquals(91, repository.count());

        seeder.seedMissing();
        assertEquals(92, repository.count());
        assertEquals(AccountStatus.SUSPENDED, repository.findByAccountNumber("SV-0000060010").orElseThrow().getAccountStatus());
    }
}
