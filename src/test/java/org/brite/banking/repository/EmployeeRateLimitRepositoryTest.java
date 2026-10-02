package org.brite.banking.repository;

import org.brite.banking.domain.EmployeeRateLimit;
import org.brite.banking.entity.EmployeeRateLimitEntity;
import org.brite.banking.repository.jpa.EmployeeRateLimitJpaRepository;
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

/** Real SQL on embedded H2: first-use row creation, per-employee limit, atomic count, day rollover and login count. */
@DataJpaTest
@ContextConfiguration(classes = EmployeeRateLimitRepositoryTest.Cfg.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EmployeeRateLimitRepositoryTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = EmployeeRateLimitJpaRepository.class)
    static class Cfg {
    }

    @Autowired
    private EmployeeRateLimitJpaRepository jpaRepository;
    private EmployeeRateLimitRepository repository;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        repository = new EmployeeRateLimitRepository(jpaRepository);
    }

    @Test
    void theFirstRequestCreatesTheEmployeesRowForTodayWithNoOwnLimit() {
        EmployeeRateLimit row = repository.findForToday("EMP-000001", today);

        assertThat(row.getEmployeeNumber()).isEqualTo("EMP-000001");
        assertThat(row.getMaxRequestsPerDay()).isNull();
        assertThat(row.getUsageDate()).isEqualTo(today);
        assertThat(row.getRequestCount()).isZero();
        assertThat(row.getLoginCount()).isZero();
        assertThat(jpaRepository.findAll().stream().filter(e -> e.getEmployeeNumber().equals("EMP-000001"))).hasSize(1);
    }

    @Test
    void requestsAreCountedUpToTheLimitAndNoFurther() {
        repository.findForToday("EMP-000002", today);

        assertThat(repository.tryConsumeRequest("EMP-000002", today, 2)).isTrue();
        assertThat(repository.tryConsumeRequest("EMP-000002", today, 2)).isTrue();
        assertThat(repository.tryConsumeRequest("EMP-000002", today, 2)).isFalse();
        assertThat(repository.findForToday("EMP-000002", today).getRequestCount()).isEqualTo(2);
    }

    @Test
    void employeesAreCountedSeparately() {
        repository.findForToday("EMP-000003", today);
        repository.findForToday("EMP-000004", today);
        repository.tryConsumeRequest("EMP-000003", today, 1);

        assertThat(repository.tryConsumeRequest("EMP-000003", today, 1)).isFalse();
        assertThat(repository.tryConsumeRequest("EMP-000004", today, 1)).isTrue();
    }

    @Test
    void aStoredOwnLimitIsReturnedWithTheRow() {
        jpaRepository.saveAndFlush(EmployeeRateLimitEntity.builder().employeeNumber("EMP-000005").maxRequestsPerDay(25).usageDate(today).build());

        assertThat(repository.findForToday("EMP-000005", today).getMaxRequestsPerDay()).isEqualTo(25);
    }

    @Test
    void aNewDayStartsTheCountersAgainButKeepsTheEmployeesLimit() {
        jpaRepository.saveAndFlush(EmployeeRateLimitEntity.builder().employeeNumber("EMP-000006").maxRequestsPerDay(25)
                .usageDate(today.minusDays(1)).requestCount(25).loginCount(4).build());

        EmployeeRateLimit row = repository.findForToday("EMP-000006", today);

        assertThat(row.getUsageDate()).isEqualTo(today);
        assertThat(row.getRequestCount()).isZero();
        assertThat(row.getLoginCount()).isZero();
        assertThat(row.getMaxRequestsPerDay()).isEqualTo(25);
        assertThat(repository.tryConsumeRequest("EMP-000006", today, 25)).isTrue();
    }

    @Test
    void loginsAreCountedPerDay() {
        repository.findForToday("EMP-000007", today);

        repository.recordLogin("EMP-000007", today);
        repository.recordLogin("EMP-000007", today);

        assertThat(repository.findForToday("EMP-000007", today).getLoginCount()).isEqualTo(2);
    }

    @Test
    void aEmployeeWithNoRowCannotBeCountedUntilItIsCreated() {
        assertThat(repository.tryConsumeRequest("EMP-000099", today, 10)).isFalse();
        assertThat(repository.find("EMP-000099")).isEmpty();
    }

    @Test
    void anOwnLimitCanBeSetClearedAndIsCreatedForAEmployeeWithNoRow() {
        repository.setMaxRequestsPerDay("EMP-000008", today, 300);
        assertThat(repository.find("EMP-000008").orElseThrow().getMaxRequestsPerDay()).isEqualTo(300);

        repository.tryConsumeRequest("EMP-000008", today, 300);
        repository.setMaxRequestsPerDay("EMP-000008", today, null);

        EmployeeRateLimit row = repository.find("EMP-000008").orElseThrow();
        assertThat(row.getMaxRequestsPerDay()).isNull();
        assertThat(row.getRequestCount()).as("today's usage is untouched").isEqualTo(1);
    }
}
