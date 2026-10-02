package org.brite.banking.service;

import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRateLimit;
import org.brite.banking.domain.EmployeeRateLimitView;
import org.brite.banking.exception.EmployeeNotFoundException;
import org.brite.banking.repository.EmployeeRepository;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.gateway.RateLimitProperties;
import org.brite.banking.repository.EmployeeRateLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test: which limit applies (the employee's own, else the employee default), and what the decision reports. */
class EmployeeQuotaServiceTest {
    private EmployeeRateLimitRepository repository;
    private EmployeeRepository employees;
    private EmployeeQuotaService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRateLimitRepository.class);
        employees = mock(EmployeeRepository.class);
        when(employees.findByEmployeeNumber("EMP-000010")).thenReturn(Optional.of(Employee.builder().employeeNumber("EMP-000010").build()));
        RateLimitProperties properties = RateLimitProperties.builder().enabled(true).requestsPerDay(5).employeeRequestsPerDay(1000).customerHeaderName("X-Customer-Id").build();
        service = new EmployeeQuotaService(repository, properties, employees);
    }

    private static EmployeeRateLimit row(Integer max, int requests) {
        return EmployeeRateLimit.builder().employeeNumber("EMP-000010").maxRequestsPerDay(max).usageDate(LocalDate.now()).requestCount(requests).build();
    }

    @Test
    void aEmployeeWithNoOwnLimitGetsTheApplicationDefault() {
        when(repository.findForToday("EMP-000010", LocalDate.now())).thenReturn(row(null, 10));
        when(repository.tryConsumeRequest("EMP-000010", LocalDate.now(), 1000)).thenReturn(true);

        RateLimitDecision decision = service.consumeRequest("EMP-000010");

        assertTrue(decision.allowed());
        assertEquals(1000, decision.limit());
        assertEquals(989, decision.remaining());
    }

    @Test
    void aEmployeesOwnLimitOverridesTheDefault() {
        when(repository.findForToday("EMP-000010", LocalDate.now())).thenReturn(row(50, 49));
        when(repository.tryConsumeRequest("EMP-000010", LocalDate.now(), 50)).thenReturn(true);

        RateLimitDecision decision = service.consumeRequest("EMP-000010");

        assertTrue(decision.allowed());
        assertEquals(50, decision.limit());
        assertEquals(0, decision.remaining());
    }

    @Test
    void whenTheDatabaseRefusesTheCountTheRequestIsNotAllowed() {
        when(repository.findForToday("EMP-000010", LocalDate.now())).thenReturn(row(50, 50));
        when(repository.tryConsumeRequest("EMP-000010", LocalDate.now(), 50)).thenReturn(false);

        RateLimitDecision decision = service.consumeRequest("EMP-000010");

        assertFalse(decision.allowed());
        assertEquals(50, decision.limit());
        assertEquals(0, decision.remaining());
    }

    @Test
    void aLoginIsCountedAfterTodaysRowExists() {
        when(repository.findForToday(any(), any())).thenReturn(row(null, 0));

        service.recordLogin("EMP-000010");

        var order = inOrder(repository);
        order.verify(repository).findForToday("EMP-000010", LocalDate.now());
        order.verify(repository).recordLogin("EMP-000010", LocalDate.now());
        verify(repository, org.mockito.Mockito.never()).tryConsumeRequest(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void theViewShowsTheDefaultLimitAndTodaysUsageForAEmployeeOnTheDefault() {
        when(repository.find("EMP-000010")).thenReturn(Optional.of(EmployeeRateLimit.builder().employeeNumber("EMP-000010").usageDate(LocalDate.now())
                .requestCount(40).loginCount(3).build()));

        EmployeeRateLimitView view = service.view("EMP-000010");

        assertEquals(1000, view.getDailyLimit());
        assertNull(view.getCustomLimit());
        assertEquals(1000, view.getDefaultLimit());
        assertEquals(40, view.getRequestsToday());
        assertEquals(960, view.getRemainingToday());
        assertEquals(3, view.getLoginsToday());
    }

    @Test
    void theViewShowsTheEmployeesOwnLimit() {
        when(repository.find("EMP-000010")).thenReturn(Optional.of(row(250, 10)));

        EmployeeRateLimitView view = service.view("EMP-000010");

        assertEquals(250, view.getDailyLimit());
        assertEquals(250, view.getCustomLimit());
        assertEquals(240, view.getRemainingToday());
    }

    @Test
    void yesterdaysUsageOrNoRowAtAllShowsAsZeroToday() {
        when(repository.find("EMP-000010")).thenReturn(Optional.of(EmployeeRateLimit.builder().employeeNumber("EMP-000010").maxRequestsPerDay(5)
                .usageDate(LocalDate.now().minusDays(1)).requestCount(5).loginCount(2).build()));
        EmployeeRateLimitView stale = service.view("EMP-000010");
        assertEquals(0, stale.getRequestsToday());
        assertEquals(0, stale.getLoginsToday());
        assertEquals(5, stale.getRemainingToday());

        when(repository.find("EMP-000010")).thenReturn(Optional.empty());
        EmployeeRateLimitView none = service.view("EMP-000010");
        assertEquals(1000, none.getDailyLimit());
        assertEquals(0, none.getRequestsToday());
    }

    @Test
    void settingTheLimitStoresItThenReturnsTheView() {
        when(repository.find("EMP-000010")).thenReturn(Optional.of(row(250, 0)));

        EmployeeRateLimitView view = service.setLimit("EMP-000010", 250);

        verify(repository).setMaxRequestsPerDay("EMP-000010", LocalDate.now(), 250);
        assertEquals(250, view.getDailyLimit());
    }

    @Test
    void anUnknownEmployeeIsNotFoundAndNothingIsWritten() {
        when(employees.findByEmployeeNumber("EMP-999999")).thenReturn(Optional.empty());

        assertThrows(EmployeeNotFoundException.class, () -> service.view("EMP-999999"));
        assertThrows(EmployeeNotFoundException.class, () -> service.setLimit("EMP-999999", 5));
        verify(repository, org.mockito.Mockito.never()).setMaxRequestsPerDay(any(), any(), any());
    }
}
