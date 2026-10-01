package org.brite.banking.repository;

import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.LocationType;
import org.brite.banking.entity.BankLocationEntity;
import org.brite.banking.entity.EmployeeEntity;
import org.brite.banking.repository.jpa.BankLocationJpaRepository;
import org.brite.banking.repository.jpa.EmployeeJpaRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real seeders against embedded H2 through a minimal context, like {@link BankLocationDataSeederTest}. */
@DataJpaTest
@ContextConfiguration(classes = EmployeeDataSeederTest.Cfg.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EmployeeDataSeederTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = EmployeeJpaRepository.class)
    @Import({EmployeeDataSeeder.class, BankLocationDataSeeder.class, EmployeeRepository.class})
    static class Cfg {
    }

    @Autowired
    private EmployeeJpaRepository repository;
    @Autowired
    private BankLocationJpaRepository locationRepository;
    @Autowired
    private EmployeeDataSeeder seeder;
    @Autowired
    private EmployeeRepository facade;

    @Test
    void seedsThreeAreaManagersSixManagersAndTwelveTellers() {
        assertEquals(21, repository.count());
        assertEquals(3, count(EmployeeRole.AREA_MANAGER));
        assertEquals(6, count(EmployeeRole.MANAGER));
        assertEquals(12, count(EmployeeRole.TELLER));
    }

    @Test
    void tellersCanDepositAndWithdrawButNotChangeAccountStatus() {
        Set<EmployeePrivilege> tellerPrivileges = EmployeeRole.TELLER.getPrivileges();
        assertTrue(tellerPrivileges.containsAll(Set.of(
                EmployeePrivilege.VIEW_ACCOUNT, EmployeePrivilege.DEPOSIT, EmployeePrivilege.WITHDRAW, EmployeePrivilege.OPEN_ACCOUNT)));
        assertFalse(tellerPrivileges.contains(EmployeePrivilege.SUSPEND_ACCOUNT));
        assertFalse(tellerPrivileges.contains(EmployeePrivilege.REACTIVATE_ACCOUNT));
        assertFalse(tellerPrivileges.contains(EmployeePrivilege.CLOSE_ACCOUNT));
    }

    @Test
    void managersCanRunAccountLifecycleAndAreaManagersAlsoManageEmployees() {
        Set<EmployeePrivilege> manager = EmployeeRole.MANAGER.getPrivileges();
        assertTrue(manager.containsAll(EmployeeRole.TELLER.getPrivileges()));
        assertTrue(manager.containsAll(Set.of(EmployeePrivilege.SUSPEND_ACCOUNT,
                EmployeePrivilege.UPDATE_SUSPENSION, EmployeePrivilege.REACTIVATE_ACCOUNT,
                EmployeePrivilege.CLOSE_ACCOUNT)));
        assertFalse(manager.contains(EmployeePrivilege.MANAGE_EMPLOYEES));
        assertEquals(Set.of(EmployeePrivilege.values()), EmployeeRole.AREA_MANAGER.getPrivileges());
    }

    @Test
    void onLeaveEmployeeHasNoEffectivePrivileges() {
        Employee onLeave = facade.findByEmployeeNumber("EMP-000016").orElseThrow();
        assertEquals(EmployeeStatus.ON_LEAVE, onLeave.getStatus());
        assertFalse(onLeave.hasPrivilege(EmployeePrivilege.DEPOSIT));
        assertTrue(facade.findByEmployeeNumber("EMP-000015").orElseThrow().hasPrivilege(EmployeePrivilege.DEPOSIT));
    }

    @Test
    void branchStaffWorkAtStaffedBranchesAndAreaManagersHaveARegionInstead() {
        Map<Long, BankLocationEntity> locations = locationRepository.findAll().stream()
                .collect(Collectors.toMap(BankLocationEntity::getId, Function.identity()));
        for (EmployeeEntity e : repository.findAll()) {
            if (e.getRole() == EmployeeRole.AREA_MANAGER) {
                assertNull(e.getBankLocationId(), e.getEmployeeNumber());
                assertNotNull(e.getRegion(), e.getEmployeeNumber());
                assertNull(e.getSupervisorId(), e.getEmployeeNumber());
            } else {
                BankLocationEntity branch = locations.get(e.getBankLocationId());
                assertNotNull(branch, e.getEmployeeNumber());
                assertNotEquals(LocationType.ATM, branch.getLocationType(), e.getEmployeeNumber());
                assertNotNull(e.getSupervisorId(), e.getEmployeeNumber());
            }
        }
    }

    @Test
    void supervisorsOutrankTheirReports() {
        Map<Long, EmployeeEntity> byId = repository.findAll().stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        for (EmployeeEntity e : byId.values()) {
            if (e.getSupervisorId() == null) {
                continue;
            }
            EmployeeRole boss = byId.get(e.getSupervisorId()).getRole();
            assertTrue(boss.ordinal() > e.getRole().ordinal(), e.getEmployeeNumber());
        }
    }

    @Test
    void facadeLooksUpByNumberAndRole() {
        assertEquals("Priya", facade.findByEmployeeNumber("EMP-000001").orElseThrow().getFirstName());
        assertTrue(facade.findByEmployeeNumber("EMP-999999").isEmpty());
        List<Employee> managers = facade.findByRole(EmployeeRole.MANAGER);
        assertEquals(6, managers.size());
    }

    @Test
    void seedingIsSkippedWhenTableAlreadyHasData() {
        seeder.seedIfEmpty();
        assertEquals(21, repository.count());
    }

    private long count(EmployeeRole role) {
        return repository.findAll().stream().filter(e -> e.getRole() == role).count();
    }
}
