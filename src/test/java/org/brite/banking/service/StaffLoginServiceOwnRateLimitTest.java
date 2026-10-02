package org.brite.banking.service;

import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test: an employee reads their own usage with no privilege, but only while ACTIVE with an ACTIVE login. */
class StaffLoginServiceOwnRateLimitTest {
    private EmployeeService employees;
    private EmployeeQuotaService quota;
    private StaffLoginService service;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeService.class);
        quota = mock(EmployeeQuotaService.class);
        service = new StaffLoginService(employees, mock(EmployeeCredentialService.class), mock(CustomerCredentialService.class),
                mock(CustomerQuotaService.class), quota);
    }

    @Test
    void anActiveEmployeeSeesTheirOwnUsage() {
        EmployeeRateLimitView view = EmployeeRateLimitView.builder().employeeNumber("EMP-000010").dailyLimit(1000).build();
        when(employees.getEmployee("EMP-000010", "EMP-000010")).thenReturn(Employee.builder().employeeNumber("EMP-000010").build());
        when(quota.view("EMP-000010")).thenReturn(view);

        assertSame(view, service.ownRateLimit("EMP-000010"));
    }

    @Test
    void anInactiveEmployeeIsRefusedAndNoUsageIsRead() {
        when(employees.getEmployee("EMP-000016", "EMP-000016")).thenThrow(new EmployeeNotAuthorizedException("ON_LEAVE"));

        assertThrows(EmployeeNotAuthorizedException.class, () -> service.ownRateLimit("EMP-000016"));
        verifyNoInteractions(quota);
    }
}
