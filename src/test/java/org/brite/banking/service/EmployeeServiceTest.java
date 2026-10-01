package org.brite.banking.service;

import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.EmployeeNotFoundException;
import org.brite.banking.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {
    private EmployeeRepository repository;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRepository.class);
        service = new EmployeeService(repository);
    }

    private Employee employee(String number, EmployeeRole role, EmployeeStatus status) {
        Employee e = Employee.builder().employeeNumber(number).role(role).status(status).build();
        when(repository.findByEmployeeNumber(number)).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void tellerMayDepositAndWithdraw() {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        assertEquals("EMP-T", service.requirePrivilege("EMP-T", EmployeePrivilege.DEPOSIT).getEmployeeNumber());
        assertEquals("EMP-T", service.requirePrivilege("EMP-T", EmployeePrivilege.WITHDRAW).getEmployeeNumber());
    }

    @Test
    void tellerMayNotSuspendReactivateOrClose() {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        for (EmployeePrivilege p : List.of(EmployeePrivilege.SUSPEND_ACCOUNT,
                EmployeePrivilege.REACTIVATE_ACCOUNT, EmployeePrivilege.CLOSE_ACCOUNT)) {
            assertThrows(EmployeeNotAuthorizedException.class, () -> service.requirePrivilege("EMP-T", p));
        }
    }

    @Test
    void managerMayRunAccountLifecycleButNotManageEmployees() {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        service.requirePrivilege("EMP-M", EmployeePrivilege.SUSPEND_ACCOUNT);
        service.requirePrivilege("EMP-M", EmployeePrivilege.REACTIVATE_ACCOUNT);
        assertThrows(EmployeeNotAuthorizedException.class,
                () -> service.requirePrivilege("EMP-M", EmployeePrivilege.MANAGE_EMPLOYEES));
    }

    @Test
    void onLeaveAndTerminatedEmployeesAreRejectedEvenWithTheRolePrivilege() {
        employee("EMP-L", EmployeeRole.AREA_MANAGER, EmployeeStatus.ON_LEAVE);
        employee("EMP-X", EmployeeRole.MANAGER, EmployeeStatus.TERMINATED);
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.requirePrivilege("EMP-L", EmployeePrivilege.DEPOSIT));
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.requirePrivilege("EMP-X", EmployeePrivilege.DEPOSIT));
    }

    @Test
    void missingHeaderIsBadRequestAndUnknownEmployeeIsNotFound() {
        assertThrows(IllegalArgumentException.class, () -> service.requirePrivilege(null, EmployeePrivilege.DEPOSIT));
        assertThrows(IllegalArgumentException.class, () -> service.requirePrivilege("  ", EmployeePrivilege.DEPOSIT));
        when(repository.findByEmployeeNumber("EMP-NONE")).thenReturn(Optional.empty());
        assertThrows(EmployeeNotFoundException.class, () -> service.requirePrivilege("EMP-NONE", EmployeePrivilege.DEPOSIT));
    }

    @Test
    void employeeCanReadOwnProfileButOthersNeedManageEmployees() {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        employee("EMP-A", EmployeeRole.AREA_MANAGER, EmployeeStatus.ACTIVE);
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);

        assertEquals("EMP-T", service.getEmployee("EMP-T", "EMP-T").getEmployeeNumber());
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.getEmployee("EMP-T", "EMP-M"));
        assertEquals("EMP-M", service.getEmployee("EMP-A", "EMP-M").getEmployeeNumber());
        when(repository.findByEmployeeNumber("EMP-NONE")).thenReturn(Optional.empty());
        assertThrows(EmployeeNotFoundException.class, () -> service.getEmployee("EMP-A", "EMP-NONE"));
    }

    @Test
    void listEmployeesNeedsManageEmployeesAndAllowListsSortKeys() {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        employee("EMP-A", EmployeeRole.AREA_MANAGER, EmployeeStatus.ACTIVE);
        PageRequest page = PageRequest.of(0, 20, Sort.by("lastName"));

        assertThrows(EmployeeNotAuthorizedException.class, () -> service.listEmployees("EMP-M", null, page));
        verify(repository, never()).search(any(), any());

        when(repository.search(EmployeeRole.TELLER, page)).thenReturn(new PageImpl<>(List.of()));
        assertNotNull(service.listEmployees("EMP-A", EmployeeRole.TELLER, page));

        assertThrows(IllegalArgumentException.class,
                () -> service.listEmployees("EMP-A", null, PageRequest.of(0, 20, Sort.by("email"))));
    }
}
