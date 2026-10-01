package org.brite.banking.repository;

import org.brite.banking.component.PasswordEncoderConfig;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.repository.jpa.AccountJpaRepository;
import org.brite.banking.repository.jpa.CustomerCredentialJpaRepository;
import org.brite.banking.rules.CustomerLoginProperties;
import org.brite.banking.service.CustomerCredentialService;
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
 * Real seeders, repositories, BCrypt and service against embedded H2, outside a test transaction
 * (so the service's own commits are what is observed, including the failed-attempt count that is
 * saved while verify() throws). Each test uses a different seeded customer.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = CustomerCredentialPersistenceTest.Cfg.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "banking.customer-login.max-failed-attempts=3"
})
class CustomerCredentialPersistenceTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = AccountJpaRepository.class)
    @EnableConfigurationProperties(CustomerLoginProperties.class)
    @Import({CustomerCredentialService.class, CustomerCredentialRepository.class, CustomerRepository.class,
            CustomerCredentialSeeder.class, AccountDataSeeder.class, PasswordEncoderConfig.class})
    static class Cfg {
    }

    @Autowired
    private CustomerCredentialService service;
    @Autowired
    private CustomerCredentialJpaRepository jpaRepository;

    @Test
    void firstTenCustomersHaveALoginStoredAsAHash() {
        assertEquals(10, jpaRepository.count());
        var row = jpaRepository.findByUsername("customer0001").orElseThrow();
        assertNotEquals("20260001", row.getPasswordHash());
        assertTrue(row.getPasswordHash().startsWith("$2"));
        assertTrue(jpaRepository.findByUsername("customer0011").isEmpty());
    }

    @Test
    void seededLoginVerifiesAndReturnsTheCustomer() {
        AuthenticatedCustomer customer = service.verify("customer0002", "20260002");
        assertEquals(2L, customer.getCustomerId());
        assertNotNull(customer.getFirstName());
        assertNotNull(jpaRepository.findByUsername("customer0002").orElseThrow().getLastLoginAt());
    }

    @Test
    void failedAttemptsArePersistedAndThreeLockTheLogin() {
        assertThrows(InvalidCredentialsException.class, () -> service.verify("customer0003", "00000000"));
        assertEquals(1, jpaRepository.findByUsername("customer0003").orElseThrow().getFailedAttempts());
        assertThrows(InvalidCredentialsException.class, () -> service.verify("customer0003", "00000000"));
        assertThrows(InvalidCredentialsException.class, () -> service.verify("customer0003", "00000000"));

        assertNotNull(jpaRepository.findByUsername("customer0003").orElseThrow().getLockedUntil());
        assertThrows(EmployeeLockedException.class, () -> service.verify("customer0003", "20260003"));
    }

    @Test
    void createLoginForACustomerWhoAlreadyHasOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createLogin(4L, "another.name", "12345678"));
    }

    @Test
    void lockingByFailuresStoresTheLockedStatusAndAnAdministratorCanReactivate() {
        for (int i = 0; i < 3; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.verify("customer0005", "00000000"));
        }
        assertEquals(LoginStatus.LOCKED, jpaRepository.findByUsername("customer0005").orElseThrow().getStatus());
        assertThrows(LoginNotActiveException.class, () -> service.requireActiveLoginIfPresent(5L));

        service.changeStatus(5L, LoginStatus.ACTIVE, "Identity confirmed");
        service.requireActiveLoginIfPresent(5L);
        assertEquals(5L, service.verify("customer0005", "20260005").getCustomerId());
    }

    @Test
    void anInactiveCustomerLoginFailsTheTransactionCheckUntilReactivated() {
        service.changeStatus(6L, LoginStatus.INACTIVE, null);
        assertThrows(LoginNotActiveException.class, () -> service.requireActiveLoginIfPresent(6L));
        service.requireActiveLoginIfPresent(1L);                       // another customer is unaffected
        service.changeStatus(6L, LoginStatus.ACTIVE, null);
        service.requireActiveLoginIfPresent(6L);
    }
}
