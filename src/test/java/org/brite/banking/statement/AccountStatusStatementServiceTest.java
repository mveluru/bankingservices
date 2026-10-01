package org.brite.banking.statement;

import org.brite.banking.domain.*;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.service.AccountStatusStatementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Spring context/MySQL) for the account-status-pulling logic:
 * date-range validation and the Account -> AccountStatusView mapping. AccountRepository
 * is mocked here since its own filtering/sorting/pagination behavior is already covered
 * by AccountRepositoryTest.
 */
@ExtendWith(MockitoExtension.class)
class AccountStatusStatementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountStatusStatementService service;

    @BeforeEach
    void setUp() {
        service = new AccountStatusStatementService(accountRepository);
    }

    @Test
    void listAccountStatuses_createdFromAfterCreatedTo_throwsIllegalArgumentExceptionWithoutTouchingRepository() {
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 1, 1);

        assertThatThrownBy(() -> service.listAccountStatuses(null, null, from, to, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    void listAccountStatuses_closedFromAfterClosedTo_throwsIllegalArgumentExceptionWithoutTouchingRepository() {
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 1, 1);

        assertThatThrownBy(() -> service.listAccountStatuses(null, AccountStatus.CLOSED, null, null, from, to, null, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    void listAccountStatuses_nonPositiveMonths_throwsIllegalArgumentExceptionWithoutTouchingRepository() {
        assertThatThrownBy(() -> service.listAccountStatuses(null, null, null, null, null, null, 0, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    void listAccountStatuses_noExplicitDates_defaultsToEighteenMonthLookback() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate expectedTo = LocalDate.now();
        LocalDate expectedFrom = expectedTo.minusMonths(AccountStatusStatementService.DEFAULT_LOOKBACK_MONTHS);
        when(accountRepository.search(null, null, expectedFrom, expectedTo, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.listAccountStatuses(null, null, null, null, null, null, null, pageable);

        verify(accountRepository).search(null, null, expectedFrom, expectedTo, null, null, pageable);
    }

    @Test
    void listAccountStatuses_monthsProvidedWithoutExplicitDates_overridesDefaultLookback() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate expectedTo = LocalDate.now();
        LocalDate expectedFrom = expectedTo.minusMonths(6);
        when(accountRepository.search(null, null, expectedFrom, expectedTo, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.listAccountStatuses(null, null, null, null, null, null, 6, pageable);

        verify(accountRepository).search(null, null, expectedFrom, expectedTo, null, null, pageable);
    }

    @Test
    void listAccountStatuses_explicitCreatedDates_ignoreMonthsAndSkipDefaultLookback() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate createdFrom = LocalDate.of(2021, 1, 1);
        LocalDate createdTo = LocalDate.of(2021, 12, 31);
        when(accountRepository.search(null, null, createdFrom, createdTo, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // months is provided but must be ignored since explicit createdFrom/createdTo win
        service.listAccountStatuses(null, null, createdFrom, createdTo, null, null, 6, pageable);

        verify(accountRepository).search(null, null, createdFrom, createdTo, null, null, pageable);
    }

    @Test
    void listAccountStatuses_mapsCheckingAccountFieldsIncludingCustomerNameAndPagination() {
        Customer customer = Customer.builder()
                .firstName("Alice").lastName("Smith").dateOfBirth(LocalDate.of(1990, 1, 1)).build();
        Account account = Account.builder()
                .checkingAccountNumber("CH-1")
                .accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE)
                .createdDate(LocalDate.of(2021, 1, 1))
                .customer(customer)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate createdFrom = LocalDate.of(2000, 1, 1);
        LocalDate createdTo = LocalDate.of(2100, 1, 1);
        when(accountRepository.search(null, null, createdFrom, createdTo, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(account), pageable, 1));

        Page<AccountStatusView> result = service.listAccountStatuses(null, null, createdFrom, createdTo, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        AccountStatusView view = result.getContent().get(0);
        assertThat(view.getAccountNumber()).isEqualTo("CH-1");
        assertThat(view.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(view.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(view.getCreatedDate()).isEqualTo(LocalDate.of(2021, 1, 1));
        assertThat(view.getClosedDate()).isNull();
        assertThat(view.getFirstName()).isEqualTo("Alice");
        assertThat(view.getLastName()).isEqualTo("Smith");
    }

    @Test
    void listAccountStatuses_savingsAccount_resolvesSavingAccountNumberWhenCheckingIsNull() {
        Account account = Account.builder()
                .savingAccountNumber("SV-1")
                .accountType(AccountType.SAVINGS)
                .accountStatus(AccountStatus.CLOSED)
                .createdDate(LocalDate.of(2020, 1, 1))
                .closedDate(LocalDate.of(2022, 1, 1))
                .customer(Customer.builder().firstName("Bob").lastName("Jones").dateOfBirth(LocalDate.of(1980, 1, 1)).build())
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate createdFrom = LocalDate.of(2000, 1, 1);
        LocalDate createdTo = LocalDate.of(2100, 1, 1);
        when(accountRepository.search(null, AccountStatus.CLOSED, createdFrom, createdTo, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(account), pageable, 1));

        Page<AccountStatusView> result = service.listAccountStatuses(null, AccountStatus.CLOSED, createdFrom, createdTo, null, null, null, pageable);

        AccountStatusView view = result.getContent().get(0);
        assertThat(view.getAccountNumber()).isEqualTo("SV-1");
        assertThat(view.getClosedDate()).isEqualTo(LocalDate.of(2022, 1, 1));
    }

    @Test
    void listAccountStatuses_accountNumberProvided_passesItThroughToRepositoryAndMapsResult() {
        Account account = Account.builder()
                .checkingAccountNumber("CH-1")
                .accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE)
                .createdDate(LocalDate.of(2021, 1, 1))
                .customer(Customer.builder().firstName("Carol").lastName("Davis").dateOfBirth(LocalDate.of(1978, 3, 15)).build())
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate createdFrom = LocalDate.of(2000, 1, 1);
        LocalDate createdTo = LocalDate.of(2100, 1, 1);
        when(accountRepository.search("CH-1", null, createdFrom, createdTo, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(account), pageable, 1));

        Page<AccountStatusView> result = service.listAccountStatuses("CH-1", null, createdFrom, createdTo, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getAccountNumber()).isEqualTo("CH-1");
    }
}
