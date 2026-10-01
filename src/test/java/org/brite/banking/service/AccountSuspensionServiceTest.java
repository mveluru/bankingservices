package org.brite.banking.service;

import org.brite.banking.domain.Account;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test (no Spring context): the time-window rules live in the service, state rules in the repository. */
class AccountSuspensionServiceTest {
    private final AccountRepository repository = mock(AccountRepository.class);
    private final AccountSuspensionService service = new AccountSuspensionService(repository);
    private final Account account = Account.builder().checkingAccountNumber("CH-0000010001").build();

    @Test
    void suspendDefaultsStartToNowAndPassesEndAndNotesThrough() {
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        when(repository.suspend(eq("CH-0000010001"), any(), eq(end), eq("Fraud review"))).thenReturn(account);
        LocalDateTime before = LocalDateTime.now();

        Account result = service.suspendAccount("CH-0000010001",
                SuspendAccountRequest.builder().notes("Fraud review").endDateTime(end).build());

        assertSame(account, result);
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).suspend(eq("CH-0000010001"), start.capture(), eq(end), eq("Fraud review"));
        assertNotNull(start.getValue());
        assertTrue(!start.getValue().isBefore(before) && !start.getValue().isAfter(LocalDateTime.now()));
    }

    @Test
    void suspendAllowsIndefiniteSuspensionAndBackdatedStart() {
        LocalDateTime start = LocalDateTime.now().minusDays(3);
        when(repository.suspend("CH-0000010001", start, null, "n")).thenReturn(account);

        assertSame(account, service.suspendAccount("CH-0000010001",
                SuspendAccountRequest.builder().notes("n").startDateTime(start).build()));
    }

    @Test
    void suspendRejectsStartInTheFutureWithoutTouchingTheRepository() {
        LocalDateTime future = LocalDateTime.now().plusHours(2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.suspendAccount(
                "CH-0000010001", SuspendAccountRequest.builder().notes("n").startDateTime(future).build()));

        assertTrue(ex.getMessage().contains("cannot be in the future"));
        verify(repository, never()).suspend(any(), any(), any(), any());
    }

    @Test
    void suspendRejectsEndNotAfterStart() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.suspendAccount(
                "CH-0000010001", SuspendAccountRequest.builder().notes("n").startDateTime(start).endDateTime(start).build()));

        assertTrue(ex.getMessage().contains("must be after suspension start"));
        verify(repository, never()).suspend(any(), any(), any(), any());
    }

    @Test
    void suspendRejectsEndInThePast() {
        LocalDateTime start = LocalDateTime.now().minusDays(3);
        LocalDateTime end = LocalDateTime.now().minusDays(1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.suspendAccount(
                "CH-0000010001", SuspendAccountRequest.builder().notes("n").startDateTime(start).endDateTime(end).build()));

        assertTrue(ex.getMessage().contains("must be in the future"));
    }

    @Test
    void updateSuspensionPassesSuppliedFieldsThrough() {
        LocalDateTime end = LocalDateTime.now().plusDays(5);
        when(repository.updateSuspension("CH-0000010001", end, "extended")).thenReturn(account);

        assertSame(account, service.updateSuspension("CH-0000010001",
                UpdateSuspensionRequest.builder().endDateTime(end).notes("extended").build()));
    }

    @Test
    void updateSuspensionWithNothingToChangeIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateSuspension("CH-0000010001", new UpdateSuspensionRequest()));

        assertEquals("Provide notes and/or endDateTime to update the suspension", ex.getMessage());
        verify(repository, never()).updateSuspension(any(), any(), any());
    }

    @Test
    void updateSuspensionRejectsEndInThePast() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSuspension("CH-0000010001",
                UpdateSuspensionRequest.builder().endDateTime(LocalDateTime.now().minusMinutes(1)).build()));
        verify(repository, never()).updateSuspension(any(), any(), any());
    }

    @Test
    void reactivateDelegatesToTheRepository() {
        when(repository.reactivate("CH-0000010001")).thenReturn(account);

        assertSame(account, service.reactivateAccount("CH-0000010001"));
    }

    @Test
    void reactivateExpiredSuspensionsReturnsTheRepositoryCount() {
        when(repository.reactivateExpiredSuspensions(any())).thenReturn(3);

        assertEquals(3, service.reactivateExpiredSuspensions());
    }
}
