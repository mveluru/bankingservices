package org.brite.banking.service;

import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CustomerRateLimit;
import org.brite.banking.domain.CustomerRateLimitView;
import org.brite.banking.exception.CustomerNotFoundException;
import org.brite.banking.repository.CustomerRepository;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.gateway.RateLimitProperties;
import org.brite.banking.repository.CustomerRateLimitRepository;
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

/** Plain unit test: which limit applies (the customer's own, else the default), and what the decision reports. */
class CustomerQuotaServiceTest {
    private CustomerRateLimitRepository repository;
    private CustomerRepository customers;
    private CustomerQuotaService service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomerRateLimitRepository.class);
        customers = mock(CustomerRepository.class);
        when(customers.findIdentityById(7L)).thenReturn(Optional.of(AuthenticatedCustomer.builder().customerId(7L).firstName("A").lastName("B").build()));
        RateLimitProperties properties = RateLimitProperties.builder().enabled(true).requestsPerDay(1000).customerHeaderName("X-Customer-Id").build();
        service = new CustomerQuotaService(repository, properties, customers);
    }

    private static CustomerRateLimit row(Integer max, int requests) {
        return CustomerRateLimit.builder().customerId(7L).maxRequestsPerDay(max).usageDate(LocalDate.now()).requestCount(requests).build();
    }

    @Test
    void aCustomerWithNoOwnLimitGetsTheApplicationDefault() {
        when(repository.findForToday(7L, LocalDate.now())).thenReturn(row(null, 10));
        when(repository.tryConsumeRequest(7L, LocalDate.now(), 1000)).thenReturn(true);

        RateLimitDecision decision = service.consumeRequest(7L);

        assertTrue(decision.allowed());
        assertEquals(1000, decision.limit());
        assertEquals(989, decision.remaining());
    }

    @Test
    void aCustomersOwnLimitOverridesTheDefault() {
        when(repository.findForToday(7L, LocalDate.now())).thenReturn(row(50, 49));
        when(repository.tryConsumeRequest(7L, LocalDate.now(), 50)).thenReturn(true);

        RateLimitDecision decision = service.consumeRequest(7L);

        assertTrue(decision.allowed());
        assertEquals(50, decision.limit());
        assertEquals(0, decision.remaining());
    }

    @Test
    void whenTheDatabaseRefusesTheCountTheRequestIsNotAllowed() {
        when(repository.findForToday(7L, LocalDate.now())).thenReturn(row(50, 50));
        when(repository.tryConsumeRequest(7L, LocalDate.now(), 50)).thenReturn(false);

        RateLimitDecision decision = service.consumeRequest(7L);

        assertFalse(decision.allowed());
        assertEquals(50, decision.limit());
        assertEquals(0, decision.remaining());
    }

    @Test
    void aLoginIsCountedAfterTodaysRowExists() {
        when(repository.findForToday(any(), any())).thenReturn(row(null, 0));

        service.recordLogin(7L);

        var order = inOrder(repository);
        order.verify(repository).findForToday(7L, LocalDate.now());
        order.verify(repository).recordLogin(7L, LocalDate.now());
        verify(repository, org.mockito.Mockito.never()).tryConsumeRequest(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void theViewShowsTheDefaultLimitAndTodaysUsageForACustomerOnTheDefault() {
        when(repository.find(7L)).thenReturn(Optional.of(CustomerRateLimit.builder().customerId(7L).usageDate(LocalDate.now())
                .requestCount(40).loginCount(3).build()));

        CustomerRateLimitView view = service.view(7L);

        assertEquals(1000, view.getDailyLimit());
        assertNull(view.getCustomLimit());
        assertEquals(1000, view.getDefaultLimit());
        assertEquals(40, view.getRequestsToday());
        assertEquals(960, view.getRemainingToday());
        assertEquals(3, view.getLoginsToday());
    }

    @Test
    void theViewShowsTheCustomersOwnLimit() {
        when(repository.find(7L)).thenReturn(Optional.of(row(250, 10)));

        CustomerRateLimitView view = service.view(7L);

        assertEquals(250, view.getDailyLimit());
        assertEquals(250, view.getCustomLimit());
        assertEquals(240, view.getRemainingToday());
    }

    @Test
    void yesterdaysUsageOrNoRowAtAllShowsAsZeroToday() {
        when(repository.find(7L)).thenReturn(Optional.of(CustomerRateLimit.builder().customerId(7L).maxRequestsPerDay(5)
                .usageDate(LocalDate.now().minusDays(1)).requestCount(5).loginCount(2).build()));
        CustomerRateLimitView stale = service.view(7L);
        assertEquals(0, stale.getRequestsToday());
        assertEquals(0, stale.getLoginsToday());
        assertEquals(5, stale.getRemainingToday());

        when(repository.find(7L)).thenReturn(Optional.empty());
        CustomerRateLimitView none = service.view(7L);
        assertEquals(1000, none.getDailyLimit());
        assertEquals(0, none.getRequestsToday());
    }

    @Test
    void settingTheLimitStoresItThenReturnsTheView() {
        when(repository.find(7L)).thenReturn(Optional.of(row(250, 0)));

        CustomerRateLimitView view = service.setLimit(7L, 250);

        verify(repository).setMaxRequestsPerDay(7L, LocalDate.now(), 250);
        assertEquals(250, view.getDailyLimit());
    }

    @Test
    void anUnknownCustomerIsNotFoundAndNothingIsWritten() {
        when(customers.findIdentityById(99L)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> service.view(99L));
        assertThrows(CustomerNotFoundException.class, () -> service.setLimit(99L, 5));
        verify(repository, org.mockito.Mockito.never()).setMaxRequestsPerDay(any(), any(), any());
    }
}
