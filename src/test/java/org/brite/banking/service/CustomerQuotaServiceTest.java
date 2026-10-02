package org.brite.banking.service;

import org.brite.banking.domain.CustomerRateLimit;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.gateway.RateLimitProperties;
import org.brite.banking.repository.CustomerRateLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test: which limit applies (the customer's own, else the default), and what the decision reports. */
class CustomerQuotaServiceTest {
    private CustomerRateLimitRepository repository;
    private CustomerQuotaService service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomerRateLimitRepository.class);
        RateLimitProperties properties = RateLimitProperties.builder().enabled(true).requestsPerDay(1000).customerHeaderName("X-Customer-Id").build();
        service = new CustomerQuotaService(repository, properties);
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
}
