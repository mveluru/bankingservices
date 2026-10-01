package org.brite.banking.service;

import org.brite.banking.exception.AccountAccessDeniedException;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerAccessServiceTest {
    private AccountRepository accounts;
    private CustomerAccessService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        service = new CustomerAccessService(accounts);
        when(accounts.findCustomerIdByAccountNumber("CH-MINE")).thenReturn(Optional.of(5L));
        when(accounts.findCustomerIdByAccountNumber("CH-THEIRS")).thenReturn(Optional.of(6L));
        when(accounts.findCustomerIdByAccountNumber("CH-NONE")).thenReturn(Optional.empty());
    }

    @Test
    void anOwnAccountIsAllowed() {
        service.requireOwnAccount(5L, "CH-MINE");
    }

    @Test
    void anotherCustomersAccountIsRefusedWithoutRevealingWhoOwnsIt() {
        AccountAccessDeniedException ex = assertThrows(AccountAccessDeniedException.class, () -> service.requireOwnAccount(5L, "CH-THEIRS"));
        assertEquals("Account CH-THEIRS does not belong to the authenticated customer", ex.getMessage());
        assertFalse(ex.getMessage().contains("6"));
    }

    @Test
    void anAccountThatDoesNotExistIsLeftToTheOperationToReportAsNotFound() {
        service.requireOwnAccount(5L, "CH-NONE");
        service.requireOwnAccount(5L, null);
    }

    @Test
    void noAuthenticatedCustomerFailsClosedWithA401() {
        assertThrows(InvalidTokenException.class, () -> service.requireAuthenticated(null));
        assertThrows(InvalidTokenException.class, () -> service.requireOwnAccount(null, "CH-MINE"));
        assertThrows(InvalidTokenException.class, () -> service.requireOwnAccounts(null, List.of("CH-MINE")));
        assertEquals(5L, service.requireAuthenticated(5L));
    }

    @Test
    void aBatchIsRefusedAsAWholeIfAnyAccountIsNotTheirs() {
        service.requireOwnAccounts(5L, List.of("CH-MINE", "CH-NONE"));
        service.requireOwnAccounts(5L, null);
        assertThrows(AccountAccessDeniedException.class, () -> service.requireOwnAccounts(5L, List.of("CH-MINE", "CH-THEIRS")));
    }
}
