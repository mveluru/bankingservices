package org.brite.banking.repository;

import org.brite.banking.domain.CustomerRateLimit;
import org.brite.banking.entity.CustomerRateLimitEntity;
import org.brite.banking.repository.jpa.CustomerRateLimitJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Real SQL on embedded H2: first-use row creation, per-customer limit, atomic count, day rollover and login count. */
@DataJpaTest
@ContextConfiguration(classes = CustomerRateLimitRepositoryTest.Cfg.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CustomerRateLimitRepositoryTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = CustomerRateLimitJpaRepository.class)
    static class Cfg {
    }

    @Autowired
    private CustomerRateLimitJpaRepository jpaRepository;
    private CustomerRateLimitRepository repository;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        repository = new CustomerRateLimitRepository(jpaRepository);
    }

    @Test
    void theFirstRequestCreatesTheCustomersRowForTodayWithNoOwnLimit() {
        CustomerRateLimit row = repository.findForToday(1L, today);

        assertThat(row.getCustomerId()).isEqualTo(1L);
        assertThat(row.getMaxRequestsPerDay()).isNull();
        assertThat(row.getUsageDate()).isEqualTo(today);
        assertThat(row.getRequestCount()).isZero();
        assertThat(row.getLoginCount()).isZero();
        assertThat(jpaRepository.findAll().stream().filter(e -> e.getCustomerId().equals(1L))).hasSize(1);
    }

    @Test
    void requestsAreCountedUpToTheLimitAndNoFurther() {
        repository.findForToday(2L, today);

        assertThat(repository.tryConsumeRequest(2L, today, 2)).isTrue();
        assertThat(repository.tryConsumeRequest(2L, today, 2)).isTrue();
        assertThat(repository.tryConsumeRequest(2L, today, 2)).isFalse();
        assertThat(repository.findForToday(2L, today).getRequestCount()).isEqualTo(2);
    }

    @Test
    void customersAreCountedSeparately() {
        repository.findForToday(3L, today);
        repository.findForToday(4L, today);
        repository.tryConsumeRequest(3L, today, 1);

        assertThat(repository.tryConsumeRequest(3L, today, 1)).isFalse();
        assertThat(repository.tryConsumeRequest(4L, today, 1)).isTrue();
    }

    @Test
    void aStoredOwnLimitIsReturnedWithTheRow() {
        jpaRepository.saveAndFlush(CustomerRateLimitEntity.builder().customerId(5L).maxRequestsPerDay(25).usageDate(today).build());

        assertThat(repository.findForToday(5L, today).getMaxRequestsPerDay()).isEqualTo(25);
    }

    @Test
    void aNewDayStartsTheCountersAgainButKeepsTheCustomersLimit() {
        jpaRepository.saveAndFlush(CustomerRateLimitEntity.builder().customerId(6L).maxRequestsPerDay(25)
                .usageDate(today.minusDays(1)).requestCount(25).loginCount(4).build());

        CustomerRateLimit row = repository.findForToday(6L, today);

        assertThat(row.getUsageDate()).isEqualTo(today);
        assertThat(row.getRequestCount()).isZero();
        assertThat(row.getLoginCount()).isZero();
        assertThat(row.getMaxRequestsPerDay()).isEqualTo(25);
        assertThat(repository.tryConsumeRequest(6L, today, 25)).isTrue();
    }

    @Test
    void loginsAreCountedPerDay() {
        repository.findForToday(7L, today);

        repository.recordLogin(7L, today);
        repository.recordLogin(7L, today);

        assertThat(repository.findForToday(7L, today).getLoginCount()).isEqualTo(2);
    }

    @Test
    void aCustomerWithNoRowCannotBeCountedUntilItIsCreated() {
        assertThat(repository.tryConsumeRequest(99L, today, 10)).isFalse();
        assertThat(repository.find(99L)).isEmpty();
    }
}
