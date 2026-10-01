package org.brite.banking.service;

import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountTransaction;
import org.brite.banking.domain.BankStatement;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.LocationType;
import org.brite.banking.domain.TransactionType;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.StatementRangeExceededException;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.repository.TransactionRepository;
import org.brite.banking.rules.AccountConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test (no Spring, no MySQL): date range rules and, above all, that customers never see which employee handled a transaction. */
class BankStatementServiceTest {
    private static final String ACCOUNT = "CH-0000088291";

    private AccountRepository accounts;
    private TransactionRepository transactions;
    private NotificationService notifications;
    private BankStatementService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        transactions = mock(TransactionRepository.class);
        notifications = mock(NotificationService.class);
        AccountConstraints constraints = AccountConstraints.builder().maxStatementRangeMonths(18).build();
        service = new BankStatementService(accounts, transactions, constraints, notifications);
        when(accounts.findByAccountNumber(ACCOUNT)).thenReturn(Optional.of(Account.builder().checkingAccountNumber(ACCOUNT).build()));
    }

    private static AccountTransaction handled(LocalDate date) {
        return AccountTransaction.builder().accountNumber(ACCOUNT).transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("50.00")).balanceAfter(new BigDecimal("550.00")).transactionDate(date).depositType("check")
                .employeeNumber("EMP-000010").employeeName("Lucas Meyer").employeeRole(EmployeeRole.TELLER)
                .bankLocationId(1L).bankLocationName("Austin Downtown Branch").bankLocationType(LocationType.OFFICE)
                .bankLocationCity("Austin").bankLocationState("TX").build();
    }

    @Test
    void statementLinesShowTheBranchOrAtmButNeverTheEmployee() {
        LocalDate begin = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 9, 24);
        AccountTransaction stored = handled(LocalDate.of(2026, 9, 1));
        when(transactions.findByAccountNumber(ACCOUNT)).thenReturn(List.of(stored));

        BankStatement statement = service.generateStatement(ACCOUNT, begin, end);

        AccountTransaction line = statement.getTransactions().get(0);
        assertNull(line.getEmployeeNumber());
        assertNull(line.getEmployeeName());
        assertNull(line.getEmployeeRole());
        assertEquals("Austin Downtown Branch", line.getBankLocationName());
        assertEquals(LocationType.OFFICE, line.getBankLocationType());
        assertEquals("Austin", line.getBankLocationCity());
        assertEquals("TX", line.getBankLocationState());
        assertEquals(new BigDecimal("50.00"), line.getAmount());
        assertEquals("check", line.getDepositType());
        assertEquals("EMP-000010", stored.getEmployeeNumber(), "the stored transaction itself is not modified");
    }

    @Test
    void onlyTransactionsInsideTheRangeAreIncludedAndTheStatementIsSent() {
        when(transactions.findByAccountNumber(ACCOUNT)).thenReturn(List.of(handled(LocalDate.of(2026, 7, 31)),
                handled(LocalDate.of(2026, 8, 1)), handled(LocalDate.of(2026, 9, 24)), handled(LocalDate.of(2026, 9, 25))));

        BankStatement statement = service.generateStatement(ACCOUNT, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 24));

        assertEquals(2, statement.getTransactions().size());
        verify(notifications).sendEmail("CH-00xxx");
        verify(notifications).sendSms("CH-00xxx");
    }

    @Test
    void anUnknownAccountABackwardsRangeAndATooLongRangeAreRejectedBeforeAnyTransactionIsRead() {
        when(accounts.findByAccountNumber("CH-NONE")).thenReturn(Optional.empty());
        assertThrows(AccountNotFoundException.class, () -> service.generateStatement("CH-NONE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.generateStatement(ACCOUNT, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)));
        assertThrows(StatementRangeExceededException.class, () -> service.generateStatement(ACCOUNT, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 9, 1)));

        verify(transactions, never()).findByAccountNumber(ACCOUNT);
        verify(notifications, never()).sendEmail("CH-00xxx");
    }
}
