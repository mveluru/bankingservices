package org.brite.banking.repository;

import org.brite.banking.domain.BankOperationServices;
import org.brite.banking.domain.LocationType;
import org.brite.banking.entity.BankLocationEntity;
import org.brite.banking.repository.jpa.BankLocationJpaRepository;
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

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real seeder against embedded H2 (like {@link AccountRepositoryTest}) so it needs no
 * MySQL. The context is a minimal one - only the bank-location entity/repository - because the
 * full application context would also boot AccountDataSeeder against H2's not-yet-created schema.
 */
@DataJpaTest
@ContextConfiguration(classes = BankLocationDataSeederTest.Cfg.class)
// application.yml hardcodes the MySQL dialect; clear it so Hibernate builds H2-compatible DDL.
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BankLocationDataSeederTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = BankLocationJpaRepository.class)
    @Import(BankLocationDataSeeder.class)
    static class Cfg {
    }

    @Autowired
    private BankLocationJpaRepository repository;

    @Autowired
    private BankLocationDataSeeder seeder;

    @Test
    void seedsTwentyLocationsOnStartup() {
        assertEquals(20, repository.count());
        assertEquals(8, count(LocationType.OFFICE));
        assertEquals(6, count(LocationType.ATM));
        assertEquals(6, count(LocationType.BOTH));
    }

    @Test
    void officesAndBranchesWithAtmsHaveCentralOfficeHoursAndPhone() {
        List<BankLocationEntity> withOffice = repository.findAll().stream()
                .filter(l -> l.getLocationType() != LocationType.ATM).toList();
        assertEquals(14, withOffice.size());
        for (BankLocationEntity l : withOffice) {
            assertEquals(LocalTime.of(8, 0), l.getOpensAt(), l.getName());
            assertEquals(LocalTime.of(16, 0), l.getClosesAt(), l.getName());
            assertEquals("America/Chicago", l.getTimeZone(), l.getName());
            assertNotNull(l.getPhoneNumber(), l.getName());
            assertTrue(l.getServices().contains(BankOperationServices.BANKING), l.getName());
        }
    }

    @Test
    void atmOnlyLocationsHaveNoOfficeHoursOrPhoneAndOnlyAtmServices() {
        List<BankLocationEntity> atms = repository.findAll().stream()
                .filter(l -> l.getLocationType() == LocationType.ATM).toList();
        for (BankLocationEntity l : atms) {
            assertNull(l.getOpensAt(), l.getName());
            assertNull(l.getClosesAt(), l.getName());
            assertNull(l.getPhoneNumber(), l.getName());
            assertTrue(l.getServices().stream().allMatch(s -> s.name().startsWith("ATM_")), l.getName());
        }
    }

    @Test
    void seedingIsSkippedWhenTableAlreadyHasData() {
        seeder.seedIfEmpty();
        assertEquals(20, repository.count());
    }

    private long count(LocationType type) {
        return repository.findAll().stream().filter(l -> l.getLocationType() == type).count();
    }
}
