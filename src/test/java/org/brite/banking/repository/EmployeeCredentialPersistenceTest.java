package org.brite.banking.repository;

import org.brite.banking.component.PasswordEncoderConfig;
import org.brite.banking.domain.Employee;
import org.brite.banking.entity.EmployeeCredentialEntity;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.repository.jpa.EmployeeCredentialJpaRepository;
import org.brite.banking.rules.EmployeeLoginProperties;
import org.brite.banking.service.EmployeeCredentialService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real seeders, repositories, BCrypt and service against embedded H2. The test runs outside a
 * transaction (NOT_SUPPORTED) so the service's own transactions commit - that is what proves a
 * failed attempt is saved even though verify() throws. Each test uses a different seeded login
 * because the data is shared across the class.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = EmployeeCredentialPersistenceTest.Cfg.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "banking.employee-login.max-failed-attempts=3"
})
class EmployeeCredentialPersistenceTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = EmployeeCredentialJpaRepository.class)
    @EnableConfigurationProperties(EmployeeLoginProperties.class)
    @Import({EmployeeCredentialService.class, EmployeeCredentialRepository.class, EmployeeRepository.class,
            EmployeeCredentialSeeder.class, EmployeeDataSeeder.class, BankLocationDataSeeder.class, PasswordEncoderConfig.class})
    static class Cfg {
    }

    @Autowired
    private EmployeeCredentialService service;
    @Autowired
    private EmployeeCredentialJpaRepository jpaRepository;

    @Test
    void everySeededEmployeeHasALoginStoredAsAHash() {
        assertEquals(21, jpaRepository.count());
        EmployeeCredentialEntity row = jpaRepository.findByUsername("priya.raman").orElseThrow();
        assertNotEquals("20260001", row.getPasswordHash());
        assertTrue(row.getPasswordHash().startsWith("$2"));
    }

    @Test
    void seededCredentialsVerifyAndReturnTheEmployee() {
        Employee employee = service.verify("lucas.meyer", "20260010");
        assertEquals("EMP-000010", employee.getEmployeeNumber());
        assertNotNull(jpaRepository.findByUsername("lucas.meyer").orElseThrow().getLastLoginAt());
    }

    @Test
    void failedAttemptsArePersistedAndThreeLockTheLogin() {
        assertThrows(InvalidCredentialsException.class, () -> service.verify("olivia.chen", "00000000"));
        assertEquals(1, jpaRepository.findByUsername("olivia.chen").orElseThrow().getFailedAttempts());
        assertThrows(InvalidCredentialsException.class, () -> service.verify("olivia.chen", "00000000"));
        assertThrows(InvalidCredentialsException.class, () -> service.verify("olivia.chen", "00000000"));

        assertNotNull(jpaRepository.findByUsername("olivia.chen").orElseThrow().getLockedUntil());
        assertThrows(EmployeeLockedException.class, () -> service.verify("olivia.chen", "20260011"));
    }

    @Test
    void aSuccessfulLoginResetsEarlierFailures() {
        assertThrows(InvalidCredentialsException.class, () -> service.verify("jamal.carter", "00000000"));
        service.verify("jamal.carter", "20260012");
        assertEquals(0, jpaRepository.findByUsername("jamal.carter").orElseThrow().getFailedAttempts());
    }

    @Test
    void createLoginForAnEmployeeWhoAlreadyHasOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createLogin("EMP-000013", "another.name", "12345678"));
    }

    @Test
    void seededLoginsAreActiveAndAnAdministratorSuspensionIsPersistedAndBlocksLogin() {
        assertEquals(LoginStatus.ACTIVE, jpaRepository.findByUsername("noah.patel").orElseThrow().getStatus());

        service.changeStatus("EMP-000014", LoginStatus.SUSPENDED, "Audit");
        var row = jpaRepository.findByUsername("noah.patel").orElseThrow();
        assertEquals(LoginStatus.SUSPENDED, row.getStatus());
        assertEquals("Audit", row.getStatusReason());
        assertNotNull(row.getStatusChangedAt());
        assertThrows(LoginNotActiveException.class, () -> service.verify("noah.patel", "20260014"));

        service.changeStatus("EMP-000014", LoginStatus.ACTIVE, null);
        assertEquals("EMP-000014", service.verify("noah.patel", "20260014").getEmployeeNumber());
    }
}
