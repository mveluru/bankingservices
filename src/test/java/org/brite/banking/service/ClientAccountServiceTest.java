package org.brite.banking.service;

import org.brite.banking.component.AccountMapper;
import org.brite.banking.component.WithdrawalMapper;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.BulkCloseAccountsResult;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.LocationType;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.domain.TransactionHandler;
import org.brite.banking.domain.TransactionType;
import org.brite.banking.exception.AccountClosedException;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.AgeException;
import org.brite.banking.exception.MaxDepositAmountException;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.repository.TransactionRepository;
import org.brite.banking.repository.WithdrawalRepository;
import org.brite.banking.request.AccountRegistrationRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.rules.AccountConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the banking business rules in ClientAccountService: age gating on
 * registration, account-number/prefix validation on withdraw/deposit, and account
 * closure. AccountRepository/TransactionRepository/WithdrawalRepository are now
 * JPA-backed (see their constructors), so they're mocked here rather than instantiated
 * directly - no Spring context or MySQL is needed for this test class either way.
 */
@ExtendWith(MockitoExtension.class)
class ClientAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WithdrawalRepository withdrawalRepository;
    @Mock
    private CustomerCredentialService customerCredentialService;

    private ClientAccountService clientAccountService;
    private AccountConstraints accountConstraints;

    @BeforeEach
    void setUp() {
        accountConstraints = AccountConstraints.builder()
                .minimumAge(18)
                .maximumDepositAmountByCash(new BigDecimal("500.00"))
                .checkingMinimumBalance(BigDecimal.ZERO)
                .savingMinimumBalance(BigDecimal.ZERO)
                .build();

        clientAccountService = new ClientAccountService(
                accountRepository,
                AccountMapper.INSTANCE,
                WithdrawalMapper.INSTANCE,
                withdrawalRepository,
                transactionRepository,
                accountConstraints,
                notificationService,
                customerCredentialService);
    }

    private AccountRegistrationRequest.AccountRegistrationRequestBuilder validRegistrationRequestBuilder() {
        return AccountRegistrationRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .street("1 Main St")
                .city("Austin")
                .state("TX")
                .zip("78701")
                .addressLine1("1 Main St")
                .accountType("CHECKING");
    }

    @Test
    void registerNewClientAccount_belowMinimumAge_throwsAgeExceptionAndDoesNotSave() {
        AccountRegistrationRequest request = validRegistrationRequestBuilder()
                .dateOfBirth(LocalDate.now().minusYears(10))
                .build();

        assertThatThrownBy(() -> clientAccountService.registerNewClientAccount(request))
                .isInstanceOf(AgeException.class);

        verifyNoInteractions(accountRepository, notificationService);
    }

    @Test
    void registerNewClientAccount_meetsMinimumAge_savesAccountAndNotifiesCustomer() {
        AccountRegistrationRequest request = validRegistrationRequestBuilder()
                .dateOfBirth(LocalDate.now().minusYears(30))
                .build();
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = clientAccountService.registerNewClientAccount(request);

        assertThat(result.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(result.getCustomer().getFirstName()).isEqualTo("John");
        verify(notificationService).sendEmail("John Doe");
        verify(notificationService).sendSms("John Doe");
    }

    @Test
    void closeAccount_delegatesToRepositoryAndReturnsItsResult() {
        Account closedAccount = Account.builder().checkingAccountNumber("CH-123").build();
        when(accountRepository.closeAccount("CH-123")).thenReturn(closedAccount);

        Account result = clientAccountService.closeAccount("CH-123");

        assertThat(result).isSameAs(closedAccount);
    }

    @Test
    void closeAccounts_allValid_returnsAllAsClosedWithNoFailures() {
        Account first = Account.builder().checkingAccountNumber("CH-1").build();
        Account second = Account.builder().savingAccountNumber("SV-1").build();
        when(accountRepository.closeAccount("CH-1")).thenReturn(first);
        when(accountRepository.closeAccount("SV-1")).thenReturn(second);

        BulkCloseAccountsResult result = clientAccountService.closeAccounts(List.of("CH-1", "SV-1"));

        assertThat(result.getClosedAccounts()).containsExactly(first, second);
        assertThat(result.getFailures()).isEmpty();
    }

    @Test
    void closeAccounts_someInvalid_closesTheValidOnesAndReportsFailuresForTheRest() {
        Account closed = Account.builder().checkingAccountNumber("CH-1").build();
        when(accountRepository.closeAccount("CH-1")).thenReturn(closed);
        when(accountRepository.closeAccount("CH-missing")).thenThrow(new AccountNotFoundException("Account not found: CH-missing"));
        when(accountRepository.closeAccount("CH-already-closed")).thenThrow(new AccountClosedException("Account CH-already-closed is already closed"));

        BulkCloseAccountsResult result = clientAccountService.closeAccounts(List.of("CH-1", "CH-missing", "CH-already-closed"));

        assertThat(result.getClosedAccounts()).containsExactly(closed);
        assertThat(result.getFailures()).hasSize(2);
        assertThat(result.getFailures())
                .extracting("accountNumber")
                .containsExactlyInAnyOrder("CH-missing", "CH-already-closed");
    }

    @Test
    void withdraw_accountNumberTooShort_throwsIllegalArgumentExceptionWithoutTouchingRepository() {
        WithdrawalRequest request = new WithdrawalRequest("C", AccountType.CHECKING, null,
                BigDecimal.TEN, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> clientAccountService.withdrawAndSaveToAccount(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    void withdraw_nonPositiveAmount_throwsIllegalArgumentException() {
        WithdrawalRequest request = new WithdrawalRequest("CH-100", AccountType.CHECKING, null,
                BigDecimal.ZERO, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> clientAccountService.withdrawAndSaveToAccount(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accountRepository, never()).withdraw(any(), any(), any());
    }

    @Test
    void withdraw_unrecognizedAccountNumberPrefix_throwsIllegalArgumentException() {
        WithdrawalRequest request = new WithdrawalRequest("XX-100", AccountType.CHECKING, null,
                BigDecimal.TEN, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> clientAccountService.withdrawAndSaveToAccount(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withdraw_requestedTypeDoesNotMatchAccountNumberPrefix_throwsIllegalArgumentException() {
        WithdrawalRequest request = new WithdrawalRequest("CH-100", AccountType.SAVINGS, null,
                BigDecimal.TEN, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> clientAccountService.withdrawAndSaveToAccount(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accountRepository, never()).withdraw(any(), any(), any());
    }

    @Test
    void deposit_invalidDepositType_throwsIllegalArgumentException() {
        DepositForm form = new DepositForm("CH-100", BigDecimal.TEN, AccountType.CHECKING, "wire",
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> clientAccountService.depositAndSaveToAccount(form))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accountRepository, never()).deposit(any(), any(), any());
    }

    @Test
    void deposit_cashAmountExceedsConfiguredMaximum_throwsMaxDepositAmountException() {
        DepositForm form = new DepositForm("CH-100", new BigDecimal("600.00"), AccountType.CHECKING, "cash",
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> clientAccountService.depositAndSaveToAccount(form))
                .isInstanceOf(MaxDepositAmountException.class);

        verify(accountRepository, never()).deposit(any(), any(), any());
    }

    @Test
    void deposit_validCashDeposit_recordsTransactionWithDepositType() {
        DepositForm form = new DepositForm("CH-100", new BigDecimal("50.00"), AccountType.CHECKING, "cash",
                null, null, null, null, null, null, null, null);
        Account updatedAccount = Account.builder().checkingAccountNumber("CH-100").checkingBalance(new BigDecimal("150.00")).build();
        when(accountRepository.deposit("CH-100", AccountType.CHECKING, new BigDecimal("50.00"))).thenReturn(updatedAccount);

        clientAccountService.depositAndSaveToAccount(form);

        verify(transactionRepository).recordTransaction(argThat(transaction ->
                transaction.getTransactionType() == TransactionType.DEPOSIT
                        && "cash".equals(transaction.getDepositType())
                        && transaction.getAmount().compareTo(new BigDecimal("50.00")) == 0
                        && transaction.getBalanceAfter().compareTo(new BigDecimal("150.00")) == 0));
    }

    @Test
    void deposit_withHandler_recordsEmployeeAndLocationOnTheTransaction() {
        DepositForm form = new DepositForm("CH-100", new BigDecimal("50.00"), AccountType.CHECKING, "check",
                null, null, null, null, null, null, null, null);
        Account updatedAccount = Account.builder().checkingAccountNumber("CH-100").checkingBalance(new BigDecimal("150.00")).build();
        when(accountRepository.deposit("CH-100", AccountType.CHECKING, new BigDecimal("50.00"))).thenReturn(updatedAccount);
        TransactionHandler handler = TransactionHandler.builder()
                .employeeNumber("EMP-000010").employeeName("Lucas Meyer").employeeRole(EmployeeRole.TELLER)
                .bankLocationId(1L).bankLocationName("Austin Downtown Branch").bankLocationType(LocationType.OFFICE)
                .bankLocationCity("Austin").bankLocationState("TX").build();

        clientAccountService.depositAndSaveToAccount(form, handler);

        verify(transactionRepository).recordTransaction(argThat(transaction ->
                "EMP-000010".equals(transaction.getEmployeeNumber())
                        && "Lucas Meyer".equals(transaction.getEmployeeName())
                        && transaction.getEmployeeRole() == EmployeeRole.TELLER
                        && Long.valueOf(1L).equals(transaction.getBankLocationId())
                        && "Austin Downtown Branch".equals(transaction.getBankLocationName())
                        && transaction.getBankLocationType() == LocationType.OFFICE));
    }

    @Test
    void deposit_withoutHandler_leavesEmployeeAndLocationNull() {
        DepositForm form = new DepositForm("CH-100", new BigDecimal("50.00"), AccountType.CHECKING, "check",
                null, null, null, null, null, null, null, null);
        Account updatedAccount = Account.builder().checkingAccountNumber("CH-100").checkingBalance(new BigDecimal("150.00")).build();
        when(accountRepository.deposit("CH-100", AccountType.CHECKING, new BigDecimal("50.00"))).thenReturn(updatedAccount);

        clientAccountService.depositAndSaveToAccount(form);

        verify(transactionRepository).recordTransaction(argThat(transaction ->
                transaction.getEmployeeNumber() == null && transaction.getBankLocationId() == null));
    }

    private DepositForm depositForm() {
        return new DepositForm("CH-100", new BigDecimal("50.00"), AccountType.CHECKING, "check",
                null, null, null, null, null, null, null, null);
    }

    @Test
    void customerInitiatedDeposit_isRejectedWhenTheOwnersLoginIsNotActive() {
        when(accountRepository.findCustomerIdByAccountNumber("CH-100")).thenReturn(Optional.of(5L));
        org.mockito.Mockito.doThrow(new LoginNotActiveException("Customer login is SUSPENDED"))
                .when(customerCredentialService).requireActiveLoginIfPresent(5L);

        assertThatThrownBy(() -> clientAccountService.depositAndSaveToAccount(depositForm()))
                .isInstanceOf(LoginNotActiveException.class);

        verify(accountRepository, never()).deposit(any(), any(), any());
        verify(transactionRepository, never()).recordTransaction(any());
    }

    @Test
    void customerInitiatedWithdrawal_isRejectedWhenTheOwnersLoginIsNotActive() {
        when(accountRepository.findCustomerIdByAccountNumber("CH-100")).thenReturn(Optional.of(5L));
        org.mockito.Mockito.doThrow(new LoginNotActiveException("Customer login is LOCKED"))
                .when(customerCredentialService).requireActiveLoginIfPresent(5L);
        WithdrawalRequest request = WithdrawalRequest.builder().AccountNumber("CH-100")
                .accountType(AccountType.CHECKING).withdrawAmount(new BigDecimal("10.00")).build();

        assertThatThrownBy(() -> clientAccountService.withdrawAndSaveToAccount(request))
                .isInstanceOf(LoginNotActiveException.class);

        verify(accountRepository, never()).withdraw(any(), any(), any());
    }

    @Test
    void staffHandledDeposit_isNotGatedOnTheCustomersLogin() {
        Account updated = Account.builder().checkingAccountNumber("CH-100").checkingBalance(new BigDecimal("150.00")).build();
        when(accountRepository.deposit("CH-100", AccountType.CHECKING, new BigDecimal("50.00"))).thenReturn(updated);

        clientAccountService.depositAndSaveToAccount(depositForm(), TransactionHandler.builder().employeeNumber("EMP-000010").build());

        verify(customerCredentialService, never()).requireActiveLoginIfPresent(any());
    }
}
