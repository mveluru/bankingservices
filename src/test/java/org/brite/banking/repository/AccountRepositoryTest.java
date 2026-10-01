package org.brite.banking.repository;

import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountTransaction;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.Address;
import org.brite.banking.domain.Customer;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.LocationType;
import org.brite.banking.domain.TransactionType;
import org.brite.banking.exception.AccountClosedException;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.AccountSuspendedException;
import org.brite.banking.exception.InsufficientFundsException;
import org.brite.banking.exception.MinBalanceException;
import org.brite.banking.repository.jpa.AccountJpaRepository;
import org.brite.banking.repository.jpa.AccountTransactionJpaRepository;
import org.brite.banking.rules.AccountConstraints;
import org.brite.BankingServicesApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the JPA-backed AccountRepository, against a real (embedded H2,
 * not the app's MySQL) database via {@code @DataJpaTest} - this replaces the old plain
 * unit test that exercised an in-memory ConcurrentHashMap directly. The filtering/
 * sorting/pagination in {@code search()} now lives in a Spring Data {@code Specification}
 * translated into real SQL, so it needs an actual database to verify; a Mockito-mocked
 * {@code AccountJpaRepository} could only assert "was called", not that the WHERE clause
 * is correct. {@code @DataJpaTest} auto-configures the embedded H2 datasource (already a
 * project dependency) instead of the MySQL one in application.yml and wraps each test in
 * a rolled-back transaction, so no live MySQL instance is needed for this class - unlike
 * the rest of the {@code @SpringBootTest} suite.
 * <p>
 * AccountRepository itself is a plain {@code @Repository} class, not a Spring Data
 * interface, so {@code @DataJpaTest}'s component scan won't auto-create it as a bean;
 * it's instantiated directly here around the real, Spring-managed {@link AccountJpaRepository}.
 */
@DataJpaTest
@ContextConfiguration(classes = BankingServicesApplication.class)
// application.yml hardcodes hibernate.dialect=MySQLDialect for the real app; @DataJpaTest
// swaps in embedded H2 for the datasource but doesn't touch that dialect override, so it
// has to be cleared here or Hibernate tries to run MySQL-flavored DDL against H2 and every
// table comes up missing.
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountRepositoryTest {

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private AccountTransactionJpaRepository transactionJpaRepository;

    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        AccountConstraints constraints = AccountConstraints.builder()
                .checkingMinimumBalance(BigDecimal.ZERO)
                .savingMinimumBalance(BigDecimal.ZERO)
                .maximumDepositAmountByCash(BigDecimal.valueOf(10_000))
                .minimumAge(18)
                .maxStatementRangeMonths(12)
                .build();
        accountRepository = new AccountRepository(accountJpaRepository, constraints);
    }

    private Account newCheckingAccount() {
        Customer customer = Customer.builder()
                .firstName("Test")
                .lastName("User")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .phoneNumber("512-555-0101")
                .address(Address.builder().street("1 Main St").city("Austin").state("TX").zip("78701").country("USA").build())
                .build();
        return Account.builder()
                .accountType(AccountType.CHECKING)
                .customer(customer)
                .build();
    }

    /**
     * save() only defaults accountStatus/createdDate when absent, so pre-setting them
     * here lets search() tests control exactly what each seeded test account looks like.
     */
    private Account seedCheckingWithDates(AccountStatus status, LocalDate createdDate, LocalDate closedDate) {
        Account account = newCheckingAccount();
        account.setAccountStatus(status);
        account.setCreatedDate(createdDate);
        account.setClosedDate(closedDate);
        return accountRepository.save(account);
    }

    @Test
    void save_newAccount_defaultsToActiveStatusWithTodayAsCreatedDate() {
        Account saved = accountRepository.save(newCheckingAccount());

        assertThat(saved.getCheckingAccountNumber()).startsWith("CH-");
        assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getCreatedDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getCheckingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void save_customerPhoneNumber_isPersistedAndReturned() {
        Account saved = accountRepository.save(newCheckingAccount());
        assertThat(saved.getCustomer().getPhoneNumber()).isEqualTo("512-555-0101");

        Account reloaded = accountRepository.findByAccountNumber(saved.getCheckingAccountNumber()).orElseThrow();
        assertThat(reloaded.getCustomer().getPhoneNumber()).isEqualTo("512-555-0101");
    }

    @Test
    void save_customerWithoutPhoneNumber_isAllowedForLegacyCustomers() {
        Account account = newCheckingAccount();
        account.getCustomer().setPhoneNumber(null);

        Account saved = accountRepository.save(account);

        assertThat(accountRepository.findByAccountNumber(saved.getCheckingAccountNumber()).orElseThrow()
                .getCustomer().getPhoneNumber()).isNull();
    }

    @Test
    void withdraw_activeAccount_reducesBalance() {
        Account saved = accountRepository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();
        accountRepository.deposit(accountNumber, AccountType.CHECKING, new BigDecimal("500.00"));

        Account updated = accountRepository.withdraw(accountNumber, AccountType.CHECKING, new BigDecimal("200.00"));

        assertThat(updated.getCheckingBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void withdraw_closedAccount_throwsAccountClosedException() {
        Account saved = accountRepository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();
        accountRepository.closeAccount(accountNumber);

        assertThatThrownBy(() -> accountRepository.withdraw(accountNumber, AccountType.CHECKING, BigDecimal.TEN))
                .isInstanceOf(AccountClosedException.class)
                .hasMessageContaining(accountNumber);
    }

    @Test
    void withdraw_amountExceedsBalance_throwsInsufficientFundsException() {
        Account saved = accountRepository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();

        assertThatThrownBy(() -> accountRepository.withdraw(accountNumber, AccountType.CHECKING, new BigDecimal("50.00")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void withdraw_resultingBalanceBelowMinimum_throwsMinBalanceException() {
        AccountConstraints strictConstraints = AccountConstraints.builder()
                .checkingMinimumBalance(new BigDecimal("100.00"))
                .savingMinimumBalance(BigDecimal.ZERO)
                .maximumDepositAmountByCash(BigDecimal.valueOf(10_000))
                .minimumAge(18)
                .build();
        AccountRepository repository = new AccountRepository(accountJpaRepository, strictConstraints);
        Account saved = repository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();
        repository.deposit(accountNumber, AccountType.CHECKING, new BigDecimal("150.00"));

        assertThatThrownBy(() -> repository.withdraw(accountNumber, AccountType.CHECKING, new BigDecimal("60.00")))
                .isInstanceOf(MinBalanceException.class);
    }

    @Test
    void withdraw_unknownAccount_throwsAccountNotFoundException() {
        assertThatThrownBy(() -> accountRepository.withdraw("CH-00000", AccountType.CHECKING, BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void deposit_activeAccount_increasesBalance() {
        Account saved = accountRepository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();

        Account updated = accountRepository.deposit(accountNumber, AccountType.CHECKING, new BigDecimal("75.00"));

        assertThat(updated.getCheckingBalance()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void deposit_closedAccount_throwsAccountClosedException() {
        Account saved = accountRepository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();
        accountRepository.closeAccount(accountNumber);

        assertThatThrownBy(() -> accountRepository.deposit(accountNumber, AccountType.CHECKING, BigDecimal.TEN))
                .isInstanceOf(AccountClosedException.class)
                .hasMessageContaining(accountNumber);
    }

    @Test
    void deposit_unknownAccount_throwsAccountNotFoundException() {
        assertThatThrownBy(() -> accountRepository.deposit("CH-00000", AccountType.CHECKING, BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void closeAccount_activeAccount_transitionsToClosedAndStampsClosedDate() {
        Account saved = accountRepository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();

        Account closed = accountRepository.closeAccount(accountNumber);

        assertThat(closed.getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(closed.getClosedDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void closeAccount_alreadyClosed_throwsAccountClosedException() {
        Account saved = accountRepository.save(newCheckingAccount());
        String accountNumber = saved.getCheckingAccountNumber();
        accountRepository.closeAccount(accountNumber);

        assertThatThrownBy(() -> accountRepository.closeAccount(accountNumber))
                .isInstanceOf(AccountClosedException.class)
                .hasMessageContaining(accountNumber);
    }

    @Test
    void closeAccount_unknownAccount_throwsAccountNotFoundException() {
        assertThatThrownBy(() -> accountRepository.closeAccount("CH-00000"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void findByAccountNumber_savedAccount_isFound() {
        Account saved = accountRepository.save(newCheckingAccount());

        assertThat(accountRepository.findByAccountNumber(saved.getCheckingAccountNumber())).isPresent();
    }

    @Test
    void findByAccountNumber_unknownAccount_isEmpty() {
        assertThat(accountRepository.findByAccountNumber("CH-does-not-exist")).isEmpty();
    }

    @Test
    void search_filtersByStatus_returnsOnlyMatchingAccounts() {
        LocalDate day = LocalDate.of(2023, 5, 15);
        seedCheckingWithDates(AccountStatus.ACTIVE, day, null);
        seedCheckingWithDates(AccountStatus.ACTIVE, day, null);
        seedCheckingWithDates(AccountStatus.CLOSED, day, day.plusMonths(1));

        Page<Account> activeOnly = accountRepository.search(null, AccountStatus.ACTIVE, day, day, null, null, null, PageRequest.of(0, 10));

        assertThat(activeOnly.getTotalElements()).isEqualTo(2);
        assertThat(activeOnly.getContent()).allMatch(a -> a.getAccountStatus() == AccountStatus.ACTIVE);
    }

    @Test
    void search_filtersByCreatedDateRange_excludesAccountsOutsideRange() {
        seedCheckingWithDates(AccountStatus.ACTIVE, LocalDate.of(2023, 1, 1), null);
        Account inRange = seedCheckingWithDates(AccountStatus.ACTIVE, LocalDate.of(2023, 6, 1), null);

        Page<Account> result = accountRepository.search(null, null,
                LocalDate.of(2023, 5, 1), LocalDate.of(2023, 12, 31), null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(inRange);
    }

    @Test
    void search_filtersByClosedDateRange_excludesAccountsOutsideRange() {
        LocalDate created = LocalDate.of(2023, 5, 15);
        Account inRange = seedCheckingWithDates(AccountStatus.CLOSED, created, LocalDate.of(2023, 8, 1));
        seedCheckingWithDates(AccountStatus.CLOSED, created, LocalDate.of(2023, 9, 15));

        Page<Account> result = accountRepository.search(null, AccountStatus.CLOSED, null, null,
                LocalDate.of(2023, 8, 1), LocalDate.of(2023, 8, 31), null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(inRange);
    }

    @Test
    void search_accountNumberProvided_returnsOnlyThatAccountWhenWithinDateRange() {
        LocalDate day = LocalDate.of(2023, 7, 1);
        Account target = seedCheckingWithDates(AccountStatus.ACTIVE, day, null);
        seedCheckingWithDates(AccountStatus.ACTIVE, day, null);

        Page<Account> result = accountRepository.search(target.getCheckingAccountNumber(), null,
                day, day, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(target);
    }

    @Test
    void search_accountNumberProvidedButOutsideDateRange_returnsEmptyPage() {
        Account target = seedCheckingWithDates(AccountStatus.ACTIVE, LocalDate.of(2023, 7, 1), null);

        Page<Account> result = accountRepository.search(target.getCheckingAccountNumber(), null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_accountNumberOmitted_returnsAllAccountsInDateRange() {
        LocalDate day = LocalDate.of(2023, 7, 1);
        seedCheckingWithDates(AccountStatus.ACTIVE, day, null);
        seedCheckingWithDates(AccountStatus.ACTIVE, day, null);

        Page<Account> result = accountRepository.search(null, null, day, day, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_paginatesResults_withCorrectTotalsAndLastPage() {
        LocalDate day = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 5; i++) {
            seedCheckingWithDates(AccountStatus.ACTIVE, day, null);
        }

        Page<Account> firstPage = accountRepository.search(null, null, day, day, null, null, null, PageRequest.of(0, 2));
        Page<Account> lastPage = accountRepository.search(null, null, day, day, null, null, null, PageRequest.of(2, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(lastPage.getContent()).hasSize(1);
    }

    @Test
    void search_sortByCreatedDateDescending_ordersNewestFirst() {
        Account oldest = seedCheckingWithDates(AccountStatus.ACTIVE, LocalDate.of(2024, 2, 1), null);
        Account middle = seedCheckingWithDates(AccountStatus.ACTIVE, LocalDate.of(2024, 2, 2), null);
        Account newest = seedCheckingWithDates(AccountStatus.ACTIVE, LocalDate.of(2024, 2, 3), null);

        Page<Account> result = accountRepository.search(null, null,
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 3), null, null,
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdDate")));

        assertThat(result.getContent()).containsExactly(newest, middle, oldest);
    }

    @Test
    void search_unsupportedSortProperty_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> accountRepository.search(null, null, null, null, null, null,
                null,
                PageRequest.of(0, 10, Sort.by("bogusField"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- suspension ----

    private String savedCheckingNumber() {
        return accountRepository.save(newCheckingAccount()).getCheckingAccountNumber();
    }

    @Test
    void suspend_activeAccount_setsStatusFlagDatesAndNotes() {
        String accountNumber = savedCheckingNumber();
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusDays(7);

        Account suspended = accountRepository.suspend(accountNumber, start, end, "Fraud review");

        assertThat(suspended.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(suspended.isSuspended()).isTrue();
        assertThat(suspended.getSuspendedStart()).isEqualTo(start);
        assertThat(suspended.getSuspendedEnd()).isEqualTo(end);
        assertThat(suspended.getSuspensionNotes()).isEqualTo("Fraud review");
    }

    @Test
    void suspend_alreadySuspended_throwsAccountSuspendedException() {
        String accountNumber = savedCheckingNumber();
        accountRepository.suspend(accountNumber, LocalDateTime.now(), null, "first");

        assertThatThrownBy(() -> accountRepository.suspend(accountNumber, LocalDateTime.now(), null, "second"))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessage("Account " + accountNumber + " is already suspended");
    }

    @Test
    void suspend_closedAccount_throwsAccountClosedException() {
        String accountNumber = savedCheckingNumber();
        accountRepository.closeAccount(accountNumber);

        assertThatThrownBy(() -> accountRepository.suspend(accountNumber, LocalDateTime.now(), null, "n"))
                .isInstanceOf(AccountClosedException.class)
                .hasMessageContaining("cannot be suspended");
    }

    @Test
    void suspend_unknownAccount_throwsAccountNotFoundException() {
        assertThatThrownBy(() -> accountRepository.suspend("CH-9999999999", LocalDateTime.now(), null, "n"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void withdrawAndDeposit_suspendedAccount_areRejectedAndBalanceIsUnchanged() {
        String accountNumber = savedCheckingNumber();
        accountRepository.deposit(accountNumber, AccountType.CHECKING, new BigDecimal("500.00"));
        accountRepository.suspend(accountNumber, LocalDateTime.now(), null, "hold");

        assertThatThrownBy(() -> accountRepository.withdraw(accountNumber, AccountType.CHECKING, BigDecimal.TEN))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessageContaining("suspended");
        assertThatThrownBy(() -> accountRepository.deposit(accountNumber, AccountType.CHECKING, BigDecimal.TEN))
                .isInstanceOf(AccountSuspendedException.class);

        assertThat(accountRepository.findByAccountNumber(accountNumber).orElseThrow().getCheckingBalance())
                .isEqualByComparingTo("500.00");
    }

    @Test
    void reactivate_suspendedAccount_clearsSuspensionAndAllowsTransactionsAgain() {
        String accountNumber = savedCheckingNumber();
        accountRepository.suspend(accountNumber, LocalDateTime.now(), LocalDateTime.now().plusDays(1), "hold");

        Account active = accountRepository.reactivate(accountNumber);

        assertThat(active.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(active.isSuspended()).isFalse();
        assertThat(active.getSuspendedStart()).isNull();
        assertThat(active.getSuspendedEnd()).isNull();
        assertThat(active.getSuspensionNotes()).isNull();
        assertThat(accountRepository.deposit(accountNumber, AccountType.CHECKING, BigDecimal.TEN).getCheckingBalance())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void reactivate_accountThatIsNotSuspended_throwsIllegalArgumentException() {
        String accountNumber = savedCheckingNumber();

        assertThatThrownBy(() -> accountRepository.reactivate(accountNumber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account " + accountNumber + " is not suspended");
    }

    @Test
    void updateSuspension_changesOnlySuppliedFields() {
        String accountNumber = savedCheckingNumber();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        accountRepository.suspend(accountNumber, start, LocalDateTime.now().plusDays(2), "original");
        LocalDateTime newEnd = LocalDateTime.now().plusDays(30);

        Account endOnly = accountRepository.updateSuspension(accountNumber, newEnd, null);
        assertThat(endOnly.getSuspendedEnd()).isEqualTo(newEnd);
        assertThat(endOnly.getSuspensionNotes()).isEqualTo("original");

        Account notesOnly = accountRepository.updateSuspension(accountNumber, null, "extended after review");
        assertThat(notesOnly.getSuspensionNotes()).isEqualTo("extended after review");
        assertThat(notesOnly.getSuspendedEnd()).isEqualTo(newEnd);
        assertThat(notesOnly.getSuspendedStart()).isEqualTo(start);
    }

    @Test
    void updateSuspension_endNotAfterStoredStart_throwsIllegalArgumentException() {
        String accountNumber = savedCheckingNumber();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        accountRepository.suspend(accountNumber, start, null, "n");

        assertThatThrownBy(() -> accountRepository.updateSuspension(accountNumber, start.minusHours(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be after suspension start");
    }

    @Test
    void updateSuspension_accountNotSuspended_throwsIllegalArgumentException() {
        String accountNumber = savedCheckingNumber();

        assertThatThrownBy(() -> accountRepository.updateSuspension(accountNumber, null, "n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not suspended");
    }

    @Test
    void closeAccount_suspendedAccount_closesItAndClearsTheSuspension() {
        String accountNumber = savedCheckingNumber();
        accountRepository.suspend(accountNumber, LocalDateTime.now(), null, "hold");

        Account closed = accountRepository.closeAccount(accountNumber);

        assertThat(closed.getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(closed.isSuspended()).isFalse();
        assertThat(closed.getSuspensionNotes()).isNull();
    }

    @Test
    void reactivateExpiredSuspensions_reactivatesOnlyFinishedOnes() {
        String expired = savedCheckingNumber();
        String running = savedCheckingNumber();
        String indefinite = savedCheckingNumber();
        LocalDateTime now = LocalDateTime.now();
        // bypass the service's "end must be in the future" rule to model a suspension that has since lapsed
        accountRepository.suspend(expired, now.minusDays(2), now.minusMinutes(1), "lapsed");
        accountRepository.suspend(running, now.minusDays(1), now.plusDays(1), "running");
        accountRepository.suspend(indefinite, now.minusDays(1), null, "indefinite");

        int reactivated = accountRepository.reactivateExpiredSuspensions(now);

        assertThat(reactivated).isEqualTo(1);
        assertThat(accountRepository.findByAccountNumber(expired).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(accountRepository.findByAccountNumber(running).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(accountRepository.findByAccountNumber(indefinite).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void search_filtersBySuspendedStatus() {
        String suspended = savedCheckingNumber();
        savedCheckingNumber();
        accountRepository.suspend(suspended, LocalDateTime.now(), null, "hold");

        Page<Account> page = accountRepository.search(null, AccountStatus.SUSPENDED, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getCheckingAccountNumber()).isEqualTo(suspended);
        assertThat(page.getContent().get(0).getSuspensionNotes()).isEqualTo("hold");
    }

    @Test
    void recordTransaction_persistsEmployeeAndLocationSnapshotAndReadsThemBack() {
        Account saved = accountRepository.save(newCheckingAccount());
        String number = saved.getCheckingAccountNumber();
        TransactionRepository transactions = new TransactionRepository(transactionJpaRepository, accountJpaRepository);

        transactions.recordTransaction(AccountTransaction.builder()
                .accountNumber(number).transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("25.00")).balanceAfter(new BigDecimal("25.00")).transactionDate(LocalDate.now())
                .employeeNumber("EMP-000010").employeeName("Lucas Meyer").employeeRole(EmployeeRole.TELLER)
                .bankLocationId(1L).bankLocationName("Austin Downtown Branch").bankLocationType(LocationType.OFFICE)
                .bankLocationCity("Austin").bankLocationState("TX").build());
        transactions.recordTransaction(AccountTransaction.builder()
                .accountNumber(number).transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("5.00")).balanceAfter(new BigDecimal("20.00")).transactionDate(LocalDate.now()).build());

        List<AccountTransaction> found = transactions.findByAccountNumber(number);
        assertThat(found).hasSize(2);
        AccountTransaction handled = found.stream().filter(t -> t.getTransactionType() == TransactionType.DEPOSIT).findFirst().orElseThrow();
        assertThat(handled.getEmployeeNumber()).isEqualTo("EMP-000010");
        assertThat(handled.getEmployeeName()).isEqualTo("Lucas Meyer");
        assertThat(handled.getEmployeeRole()).isEqualTo(EmployeeRole.TELLER);
        assertThat(handled.getBankLocationName()).isEqualTo("Austin Downtown Branch");
        assertThat(handled.getBankLocationType()).isEqualTo(LocationType.OFFICE);
        AccountTransaction customerInitiated = found.stream().filter(t -> t.getTransactionType() == TransactionType.WITHDRAWAL).findFirst().orElseThrow();
        assertThat(customerInitiated.getEmployeeNumber()).isNull();
        assertThat(customerInitiated.getBankLocationId()).isNull();
    }

    @Test
    void search_customerId_restrictsResultsToThatCustomersAccountsAndCombinesWithOtherFilters() {
        Account mine = accountRepository.save(newCheckingAccount());
        Account theirs = accountRepository.save(newCheckingAccount());
        Long myCustomerId = accountJpaRepository.findByAccountNumber(mine.getCheckingAccountNumber()).orElseThrow().getCustomer().getId();
        Long theirCustomerId = accountJpaRepository.findByAccountNumber(theirs.getCheckingAccountNumber()).orElseThrow().getCustomer().getId();
        assertThat(myCustomerId).isNotEqualTo(theirCustomerId);

        Page<Account> own = accountRepository.search(null, null, null, null, null, null, myCustomerId, PageRequest.of(0, 10));
        assertThat(own.getContent()).extracting(Account::getCheckingAccountNumber).containsExactly(mine.getCheckingAccountNumber());

        // another customer's account number is simply not found through my scope
        assertThat(accountRepository.search(theirs.getCheckingAccountNumber(), null, null, null, null, null, myCustomerId, PageRequest.of(0, 10)).getTotalElements()).isZero();
        assertThat(accountRepository.search(null, null, null, null, null, null, null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(accountRepository.findCustomerIdByAccountNumber(mine.getCheckingAccountNumber())).contains(myCustomerId);
    }
}
