package org.bee.banking.bff;

import org.bee.banking.bff.config.PortalProperties;
import org.bee.banking.bff.dto.AccountOverviewResponse;
import org.bee.banking.bff.dto.OpenAccountResponse;
import org.bee.banking.bff.dto.PortalHomeResponse;
import org.bee.banking.bff.service.PortalOrchestrationService;
import org.bee.banking.domain.Account;
import org.bee.banking.domain.BankStatement;
import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountStatusView;
import org.bee.banking.domain.AccountTransaction;
import org.bee.banking.domain.AccountType;
import org.bee.banking.domain.BankAddress;
import org.bee.banking.domain.BankLocations;
import org.bee.banking.domain.Customer;
import org.bee.banking.domain.DepositForm;
import org.bee.banking.domain.LocationType;
import org.bee.banking.domain.TransactionType;
import org.bee.banking.exception.AccountNotFoundException;
import org.bee.banking.exception.AccountClosedException;
import org.bee.banking.exception.AccountSuspendedException;
import org.bee.banking.repository.TransactionRepository;
import org.bee.banking.request.AccountRegistrationRequest;
import org.bee.banking.request.SuspendAccountRequest;
import org.bee.banking.request.UpdateSuspensionRequest;
import org.bee.banking.request.WithdrawalRequest;
import org.bee.banking.service.AccountStatusStatementService;
import org.bee.banking.service.AccountSuspensionService;
import org.bee.banking.service.BankStatementService;
import org.bee.banking.service.ClientAccountService;
import org.bee.banking.service.LocationBasedOperationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test (no Spring context): the BFF only composes mocked banking services. */
class PortalOrchestrationServiceTest {
    private final ClientAccountService clientAccountService = mock(ClientAccountService.class);
    private final AccountSuspensionService suspensionService = mock(AccountSuspensionService.class);
    private final AccountStatusStatementService statusService = mock(AccountStatusStatementService.class);
    private final BankStatementService bankStatementService = mock(BankStatementService.class);
    private final LocationBasedOperationService locationService = mock(LocationBasedOperationService.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final PortalProperties properties = new PortalProperties();
    private final PortalOrchestrationService service = new PortalOrchestrationService(
            clientAccountService, suspensionService, statusService, bankStatementService, locationService, transactionRepository, properties);

    private static Account checking(String number, BigDecimal balance) {
        return Account.builder().checkingAccountNumber(number).checkingBalance(balance)
                .accountType(AccountType.CHECKING).accountStatus(AccountStatus.ACTIVE)
                .createdDate(LocalDate.now().minusMonths(3))
                .customer(Customer.builder().firstName("Ada").lastName("Lovelace").dateOfBirth(LocalDate.of(1990, 1, 1)).phoneNumber("512-555-0101").build()).build();
    }

    private static BankLocations austinBranch() {
        return BankLocations.builder().id(1L).name("Austin Downtown Branch").locationType(LocationType.OFFICE)
                .bankAddress(BankAddress.builder().addressLine1("300 Congress Ave").city("Austin").state("TX").zip("78701").build())
                .build();
    }

    private static AccountTransaction tx(LocalDate date, String amount) {
        return AccountTransaction.builder().accountNumber("CH-0000088291").transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal(amount)).balanceAfter(new BigDecimal("500.00")).transactionDate(date).build();
    }

    private static AccountStatusView view(String number, AccountStatus status, LocalDate created) {
        return AccountStatusView.builder().accountNumber(number).accountType(AccountType.CHECKING).accountStatus(status)
                .suspended(status == AccountStatus.SUSPENDED).createdDate(created).firstName("Ada").lastName("Lovelace").build();
    }

    @Test
    void homeCombinesActiveAccountsAndNearbyLocations() {
        when(statusService.listAccountStatuses(any(), eq(AccountStatus.ACTIVE), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(view("CH-0000088291", AccountStatus.ACTIVE, LocalDate.now().minusMonths(2))), PageRequest.of(0, 5), 12));
        when(statusService.listAccountStatuses(any(), eq(AccountStatus.SUSPENDED), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));
        when(locationService.listLocations(any(), any(), eq("TX"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(austinBranch())));

        PortalHomeResponse home = service.home("TX");

        assertEquals(12, home.totalActiveAccounts());
        assertEquals(0, home.totalSuspendedAccounts());
        assertEquals("CH-0000088291", home.accounts().get(0).accountNumber());
        assertEquals("Austin", home.nearbyLocations().get(0).city());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(statusService).listAccountStatuses(eq(null), eq(AccountStatus.ACTIVE), any(), any(), any(), any(), any(), pageable.capture());
        assertEquals(properties.getHomeAccountLimit(), pageable.getValue().getPageSize());
    }

    @Test
    void homeMergesActiveAndSuspendedNewestFirstAndCapsAtTheLimit() {
        properties.setHomeAccountLimit(3);
        LocalDate today = LocalDate.now();
        when(statusService.listAccountStatuses(any(), eq(AccountStatus.ACTIVE), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(view("A1", AccountStatus.ACTIVE, today.minusMonths(1)), view("A2", AccountStatus.ACTIVE, today.minusMonths(5))), PageRequest.of(0, 3), 40));
        when(statusService.listAccountStatuses(any(), eq(AccountStatus.SUSPENDED), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(view("S1", AccountStatus.SUSPENDED, today.minusMonths(3)), view("S2", AccountStatus.SUSPENDED, today.minusMonths(9))), PageRequest.of(0, 3), 20));
        when(locationService.listLocations(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        PortalHomeResponse home = service.home(null);

        assertEquals(40, home.totalActiveAccounts());
        assertEquals(20, home.totalSuspendedAccounts());
        assertEquals(List.of("A1", "S1", "A2"), home.accounts().stream().map(a -> a.accountNumber()).toList());
        assertEquals(true, home.accounts().get(1).suspended());
    }

    @Test
    void overviewReturnsBalanceAndRecentActivityNewestFirstWithinWindow() {
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(checking("CH-0000088291", new BigDecimal("500.00"))));
        LocalDate today = LocalDate.now();
        when(transactionRepository.findByAccountNumber("CH-0000088291")).thenReturn(List.of(
                tx(today.minusDays(20), "10.00"), tx(today.minusDays(2), "20.00"), tx(today.minusDays(45), "99.00")));

        AccountOverviewResponse overview = service.overview("CH-0000088291", 30);

        assertEquals(new BigDecimal("500.00"), overview.balance());
        assertEquals("Ada", overview.firstName());
        assertEquals(30, overview.activityDays());
        assertEquals(2, overview.recentActivity().size());
        assertEquals(new BigDecimal("20.00"), overview.recentActivity().get(0).amount());
    }

    @Test
    void overviewUsesSavingFieldsForASavingsAccount() {
        Account savings = Account.builder().savingAccountNumber("SV-0000044102").savingBalance(new BigDecimal("900.00"))
                .accountType(AccountType.SAVINGS).accountStatus(AccountStatus.ACTIVE).build();
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(savings));
        when(transactionRepository.findByAccountNumber("SV-0000044102")).thenReturn(List.of());

        AccountOverviewResponse overview = service.overview("SV-0000044102", null);

        assertEquals("SV-0000044102", overview.accountNumber());
        assertEquals(new BigDecimal("900.00"), overview.balance());
        assertEquals(properties.getDefaultActivityDays(), overview.activityDays());
    }

    @Test
    void overviewThrowsNotFoundForUnknownAccount() {
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.empty());

        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class, () -> service.overview("CH-0000000000", null));
        assertEquals("Account not found: CH-0000000000", ex.getMessage());
    }

    @Test
    void overviewRejectsDaysOutsideAllowedRangeWithoutCallingServices() {
        IllegalArgumentException tooMany = assertThrows(IllegalArgumentException.class, () -> service.overview("CH-0000088291", 91));
        assertEquals("days must be between 1 and 90", tooMany.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.overview("CH-0000088291", 0));
        verify(clientAccountService, never()).lookupAccountDetails(any());
    }

    @Test
    void openAccountReturnsNewAccountAndBranchesInCustomersState() {
        AccountRegistrationRequest request = AccountRegistrationRequest.builder().state("TX").accountType("checking").build();
        when(clientAccountService.registerNewClientAccount(request)).thenReturn(checking("CH-0000010053", new BigDecimal("0.00")));
        when(locationService.listLocations(any(), any(), eq("TX"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(austinBranch())));

        OpenAccountResponse response = service.openAccount(request);

        assertEquals("CH-0000010053", response.account().accountNumber());
        assertEquals(0, response.account().recentActivity().size());
        assertEquals("Austin Downtown Branch", response.nearbyLocations().get(0).name());
    }

    @Test
    void overviewShowsSuspensionState() {
        LocalDateTime until = LocalDateTime.now().plusDays(5);
        Account suspended = checking("CH-0000010001", new BigDecimal("75.00"));
        suspended.setAccountStatus(AccountStatus.SUSPENDED);
        suspended.setSuspended(true);
        suspended.setSuspendedEnd(until);
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(suspended));
        when(transactionRepository.findByAccountNumber("CH-0000010001")).thenReturn(List.of());

        AccountOverviewResponse overview = service.overview("CH-0000010001", null);

        assertEquals(AccountStatus.SUSPENDED, overview.accountStatus());
        assertEquals(true, overview.suspended());
        assertEquals(until, overview.suspendedUntil());
    }

    private Account suspendedChecking(String number, LocalDateTime until) {
        Account a = checking(number, new BigDecimal("75.00"));
        a.setAccountStatus(AccountStatus.SUSPENDED);
        a.setSuspended(true);
        a.setSuspendedEnd(until);
        return a;
    }

    @Test
    void suspendSuspendsThroughTheServiceThenReturnsTheRefreshedOverview() {
        LocalDateTime until = LocalDateTime.now().plusDays(3);
        SuspendAccountRequest request = SuspendAccountRequest.builder().notes("Fraud review").endDateTime(until).build();
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(suspendedChecking("CH-0000010001", until)));
        when(transactionRepository.findByAccountNumber("CH-0000010001")).thenReturn(List.of());

        AccountOverviewResponse overview = service.suspend("CH-0000010001", request);

        assertEquals(AccountStatus.SUSPENDED, overview.accountStatus());
        assertEquals(true, overview.suspended());
        assertEquals(until, overview.suspendedUntil());
        var order = inOrder(suspensionService, clientAccountService);
        order.verify(suspensionService).suspendAccount("CH-0000010001", request);
        order.verify(clientAccountService).lookupAccountDetails(any());
    }

    @Test
    void suspendRuleViolationPropagatesAndNothingIsReloaded() {
        SuspendAccountRequest request = SuspendAccountRequest.builder().notes("n").build();
        when(suspensionService.suspendAccount("CH-0000010001", request))
                .thenThrow(new AccountSuspendedException("Account CH-0000010001 is already suspended"));

        assertThrows(AccountSuspendedException.class, () -> service.suspend("CH-0000010001", request));
        verify(clientAccountService, never()).lookupAccountDetails(any());
    }

    @Test
    void updateSuspensionDelegatesThenReturnsTheRefreshedOverview() {
        LocalDateTime until = LocalDateTime.now().plusDays(9);
        UpdateSuspensionRequest request = UpdateSuspensionRequest.builder().endDateTime(until).build();
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(suspendedChecking("CH-0000010001", until)));
        when(transactionRepository.findByAccountNumber("CH-0000010001")).thenReturn(List.of());

        AccountOverviewResponse overview = service.updateSuspension("CH-0000010001", request);

        assertEquals(until, overview.suspendedUntil());
        verify(suspensionService).updateSuspension("CH-0000010001", request);
    }

    @Test
    void reactivateReturnsAnActiveOverview() {
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(checking("CH-0000010001", new BigDecimal("75.00"))));
        when(transactionRepository.findByAccountNumber("CH-0000010001")).thenReturn(List.of());

        AccountOverviewResponse overview = service.reactivate("CH-0000010001");

        assertEquals(AccountStatus.ACTIVE, overview.accountStatus());
        assertEquals(false, overview.suspended());
        verify(suspensionService).reactivateAccount("CH-0000010001");
    }

    @Test
    void withdrawAndDepositReturnTheRefreshedOverview() {
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().AccountNumber("CH-0000088291").withdrawAmount(new BigDecimal("20.00")).build();
        DepositForm deposit = new DepositForm();
        deposit.setAccountNumber("CH-0000088291");
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(checking("CH-0000088291", new BigDecimal("480.00"))));
        when(transactionRepository.findByAccountNumber("CH-0000088291")).thenReturn(List.of());

        assertEquals(new BigDecimal("480.00"), service.withdraw(withdrawal).balance());
        assertEquals(new BigDecimal("480.00"), service.deposit(deposit).balance());
        verify(clientAccountService).withdrawAndSaveToAccount(withdrawal);
        verify(clientAccountService).depositAndSaveToAccount(deposit);
    }

    @Test
    void closeReturnsTheRefreshedOverview() {
        Account closed = checking("CH-0000010001", BigDecimal.ZERO);
        closed.setAccountStatus(AccountStatus.CLOSED);
        closed.setClosedDate(LocalDate.now());
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(closed));
        when(transactionRepository.findByAccountNumber("CH-0000010001")).thenReturn(List.of());

        AccountOverviewResponse overview = service.close("CH-0000010001");

        assertEquals(AccountStatus.CLOSED, overview.accountStatus());
        assertEquals(LocalDate.now(), overview.closedDate());
        verify(clientAccountService).closeAccount("CH-0000010001");
    }

    @Test
    void closeOfAnAlreadyClosedAccountPropagatesWithoutBuildingAnOverview() {
        when(clientAccountService.closeAccount("CH-0000030001"))
                .thenThrow(new AccountClosedException("Account CH-0000030001 is already closed"));

        assertThrows(AccountClosedException.class, () -> service.close("CH-0000030001"));
        verify(clientAccountService, never()).lookupAccountDetails(any());
    }

    @Test
    void statementDelegatesToTheBankStatementService() {
        LocalDate begin = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 9, 24);
        BankStatement statement = BankStatement.builder().accountNumber("CH-0000088291").beginDate(begin).endDate(end)
                .transactions(List.of()).build();
        when(bankStatementService.generateStatement("CH-0000088291", begin, end)).thenReturn(statement);

        assertEquals(statement, service.statement("CH-0000088291", begin, end));
    }

    @Test
    void transactionOnASuspendedAccountIsRejectedAndNoOverviewIsBuilt() {
        WithdrawalRequest withdrawal = WithdrawalRequest.builder().AccountNumber("CH-0000050001").withdrawAmount(BigDecimal.TEN).build();
        when(clientAccountService.withdrawAndSaveToAccount(withdrawal))
                .thenThrow(new AccountSuspendedException("Account CH-0000050001 is suspended and cannot be used for transactions until it is reactivated"));

        assertThrows(AccountSuspendedException.class, () -> service.withdraw(withdrawal));
        verify(clientAccountService, never()).lookupAccountDetails(any());
    }

    @Test
    void homeRowsCarryTheSuspensionState() {
        LocalDateTime until = LocalDateTime.now().plusDays(2);
        AccountStatusView suspended = AccountStatusView.builder().accountNumber("CH-0000050001").accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.SUSPENDED).suspended(true).suspendedEnd(until)
                .createdDate(LocalDate.now().minusMonths(6)).firstName("Nora").lastName("Adams").build();
        when(statusService.listAccountStatuses(any(), eq(AccountStatus.ACTIVE), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));
        when(statusService.listAccountStatuses(any(), eq(AccountStatus.SUSPENDED), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(suspended), PageRequest.of(0, 5), 1));
        when(locationService.listLocations(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        PortalHomeResponse home = service.home(null);

        assertEquals(true, home.accounts().get(0).suspended());
        assertEquals(until, home.accounts().get(0).suspendedUntil());
    }

    @Test
    void overviewShowsOnlyTheLastFourDigitsOfThePhoneNumber() {
        when(clientAccountService.lookupAccountDetails(any())).thenReturn(Optional.of(checking("CH-0000088291", new BigDecimal("10.00"))));
        when(transactionRepository.findByAccountNumber("CH-0000088291")).thenReturn(List.of());

        AccountOverviewResponse overview = service.overview("CH-0000088291", null);

        assertEquals("***-***-0101", overview.maskedPhoneNumber());
    }

    @Test
    void maskPhoneHandlesMissingAndUnusualValues() {
        assertEquals(null, PortalOrchestrationService.maskPhone(null));
        assertEquals(null, PortalOrchestrationService.maskPhone("  "));
        assertEquals("***", PortalOrchestrationService.maskPhone("12"));
        assertEquals("***-***-4567", PortalOrchestrationService.maskPhone("(512) 555-4567"));
    }
}
