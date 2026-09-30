package org.bee.banking.bff;

import org.bee.banking.bff.config.PortalProperties;
import org.bee.banking.bff.dto.AccountOverviewResponse;
import org.bee.banking.bff.dto.OpenAccountResponse;
import org.bee.banking.bff.dto.PortalHomeResponse;
import org.bee.banking.bff.service.PortalOrchestrationService;
import org.bee.banking.domain.Account;
import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountStatusView;
import org.bee.banking.domain.AccountTransaction;
import org.bee.banking.domain.AccountType;
import org.bee.banking.domain.BankAddress;
import org.bee.banking.domain.BankLocations;
import org.bee.banking.domain.Customer;
import org.bee.banking.domain.LocationType;
import org.bee.banking.domain.TransactionType;
import org.bee.banking.exception.AccountNotFoundException;
import org.bee.banking.repository.TransactionRepository;
import org.bee.banking.request.AccountRegistrationRequest;
import org.bee.banking.service.AccountStatusStatementService;
import org.bee.banking.service.ClientAccountService;
import org.bee.banking.service.LocationBasedOperationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test (no Spring context): the BFF only composes mocked banking services. */
class PortalOrchestrationServiceTest {
    private final ClientAccountService clientAccountService = mock(ClientAccountService.class);
    private final AccountStatusStatementService statusService = mock(AccountStatusStatementService.class);
    private final LocationBasedOperationService locationService = mock(LocationBasedOperationService.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final PortalProperties properties = new PortalProperties();
    private final PortalOrchestrationService service = new PortalOrchestrationService(
            clientAccountService, statusService, locationService, transactionRepository, properties);

    private static Account checking(String number, BigDecimal balance) {
        return Account.builder().checkingAccountNumber(number).checkingBalance(balance)
                .accountType(AccountType.CHECKING).accountStatus(AccountStatus.ACTIVE)
                .createdDate(LocalDate.now().minusMonths(3))
                .customer(Customer.builder().firstName("Ada").lastName("Lovelace").dateOfBirth(LocalDate.of(1990, 1, 1)).build()).build();
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

    @Test
    void homeCombinesActiveAccountsAndNearbyLocations() {
        AccountStatusView view = AccountStatusView.builder().accountNumber("CH-0000088291").accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE).createdDate(LocalDate.now().minusMonths(2)).firstName("Ada").lastName("Lovelace").build();
        when(statusService.listAccountStatuses(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(0, 5), 12));
        when(locationService.listLocations(any(), any(), eq("TX"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(austinBranch())));

        PortalHomeResponse home = service.home("TX");

        assertEquals(12, home.totalActiveAccounts());
        assertEquals("CH-0000088291", home.accounts().get(0).accountNumber());
        assertEquals("Austin", home.nearbyLocations().get(0).city());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(statusService).listAccountStatuses(eq(null), eq(AccountStatus.ACTIVE), any(), any(), any(), any(), any(), pageable.capture());
        assertEquals(properties.getHomeAccountLimit(), pageable.getValue().getPageSize());
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
}
