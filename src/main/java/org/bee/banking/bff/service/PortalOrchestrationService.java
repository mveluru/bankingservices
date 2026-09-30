package org.bee.banking.bff.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bee.banking.bff.config.PortalProperties;
import org.bee.banking.bff.dto.AccountOverviewResponse;
import org.bee.banking.bff.dto.OpenAccountResponse;
import org.bee.banking.bff.dto.PortalAccountSummary;
import org.bee.banking.bff.dto.PortalActivityItem;
import org.bee.banking.bff.dto.PortalHomeResponse;
import org.bee.banking.bff.dto.PortalLocation;
import org.bee.banking.domain.Account;
import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountStatusView;
import org.bee.banking.domain.AccountTransaction;
import org.bee.banking.domain.BankAddress;
import org.bee.banking.domain.BankLocations;
import org.bee.banking.domain.Customer;
import org.bee.banking.exception.AccountNotFoundException;
import org.bee.banking.messages.BankingMessages;
import org.bee.banking.repository.TransactionRepository;
import org.bee.banking.request.AccountLookupRequest;
import org.bee.banking.request.AccountRegistrationRequest;
import org.bee.banking.service.AccountStatusStatementService;
import org.bee.banking.service.ClientAccountService;
import org.bee.banking.service.LocationBasedOperationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * BFF orchestration for the banking UI portal: composes the existing banking services, in
 * process, into screen-shaped payloads so the portal makes one call per screen. Owns no
 * business rules; those stay in the services it calls. Reads only, so unlike
 * {@code BankStatementService.generateStatement} it never triggers email/SMS notifications.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortalOrchestrationService {
    private final ClientAccountService clientAccountService;
    private final AccountStatusStatementService accountStatusStatementService;
    private final LocationBasedOperationService locationService;
    private final TransactionRepository transactionRepository;
    private final PortalProperties properties;

    /** Active accounts (newest first) plus nearby locations; {@code state} is optional. */
    public PortalHomeResponse home(String state) {
        log.debug(BankingMessages.LOG_PORTAL_HOME, state);
        Page<AccountStatusView> accounts = accountStatusStatementService.listAccountStatuses(
                null, AccountStatus.ACTIVE, null, null, null, null, null,
                PageRequest.of(0, properties.getHomeAccountLimit(), Sort.by(Sort.Direction.DESC, "createdDate")));
        return new PortalHomeResponse(
                accounts.getTotalElements(),
                accounts.getContent().stream().map(this::toSummary).toList(),
                nearbyLocations(state));
    }

    /**
     * Account detail with balance and the last {@code days} days of activity, newest first.
     *
     * @param days null = {@link PortalProperties#getDefaultActivityDays()}
     * @throws AccountNotFoundException (mapped to 404) if the account doesn't exist
     * @throws IllegalArgumentException (mapped to 400) if {@code days} is outside 1..maxActivityDays
     */
    public AccountOverviewResponse overview(String accountNumber, Integer days) {
        int window = days == null ? properties.getDefaultActivityDays() : days;
        if (window < 1 || window > properties.getMaxActivityDays()) {
            throw new IllegalArgumentException(
                    String.format(BankingMessages.PORTAL_ACTIVITY_DAYS_INVALID, properties.getMaxActivityDays()));
        }
        log.debug(BankingMessages.LOG_PORTAL_OVERVIEW, accountNumber, window);

        AccountLookupRequest lookup = new AccountLookupRequest();
        lookup.setAccountNumber(accountNumber);
        Account account = clientAccountService.lookupAccountDetails(lookup)
                .orElseThrow(() -> new AccountNotFoundException(
                        String.format(BankingMessages.ACCOUNT_NOT_FOUND, accountNumber)));

        LocalDate since = LocalDate.now().minusDays(window);
        List<PortalActivityItem> activity = transactionRepository.findByAccountNumber(accountNumber).stream()
                .filter(t -> !t.getTransactionDate().isBefore(since))
                .sorted(Comparator.comparing(AccountTransaction::getTransactionDate).reversed())
                .limit(properties.getActivityLimit())
                .map(this::toActivityItem)
                .toList();
        return toOverview(account, window, activity);
    }

    /** Opens the account (all rules and notifications live in {@link ClientAccountService}) and adds nearby branches. */
    public OpenAccountResponse openAccount(AccountRegistrationRequest request) {
        Account account = clientAccountService.registerNewClientAccount(request);
        log.info(BankingMessages.LOG_PORTAL_ACCOUNT_OPENED, request.getState());
        return new OpenAccountResponse(
                toOverview(account, properties.getDefaultActivityDays(), List.of()),
                nearbyLocations(request.getState()));
    }

    private List<PortalLocation> nearbyLocations(String state) {
        return locationService.listLocations(null, null, state, null, null,
                        PageRequest.of(0, properties.getNearbyLocationLimit(), Sort.by("name")))
                .getContent().stream().map(this::toLocation).toList();
    }

    private PortalAccountSummary toSummary(AccountStatusView v) {
        return new PortalAccountSummary(v.getAccountNumber(), v.getAccountType(), v.getAccountStatus(),
                v.getCreatedDate(), v.getClosedDate(), v.getFirstName(), v.getLastName());
    }

    /** {@code Account} pairs a checking and a saving field set; exactly one is populated. */
    private AccountOverviewResponse toOverview(Account a, int days, List<PortalActivityItem> activity) {
        boolean checking = a.getCheckingAccountNumber() != null;
        Customer c = a.getCustomer();
        return new AccountOverviewResponse(
                checking ? a.getCheckingAccountNumber() : a.getSavingAccountNumber(),
                a.getAccountType(), a.getAccountStatus(),
                checking ? a.getCheckingBalance() : a.getSavingBalance(),
                a.isSuspended(), a.getSuspendedEnd(),
                a.getCreatedDate(), a.getClosedDate(),
                c == null ? null : c.getFirstName(), c == null ? null : c.getLastName(),
                days, activity);
    }

    private PortalActivityItem toActivityItem(AccountTransaction t) {
        return new PortalActivityItem(t.getTransactionType(), t.getAmount(), t.getBalanceAfter(),
                t.getTransactionDate(), t.getDepositType());
    }

    private PortalLocation toLocation(BankLocations l) {
        BankAddress a = l.getBankAddress();
        return new PortalLocation(l.getId(), l.getName(), l.getLocationType(),
                a == null ? null : a.getAddressLine1(), a == null ? null : a.getCity(),
                a == null ? null : a.getState(), a == null ? null : a.getZip(),
                l.getOpensAt(), l.getClosesAt(), l.getTimeZone(), l.getPhoneNumber(), l.getServices());
    }
}
