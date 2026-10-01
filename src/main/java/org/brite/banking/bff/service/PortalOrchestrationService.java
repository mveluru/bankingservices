package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.bff.config.PortalProperties;
import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.dto.OpenAccountResponse;
import org.brite.banking.bff.dto.PortalAccountSummary;
import org.brite.banking.bff.dto.PortalActivityItem;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLocation;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountStatusView;
import org.brite.banking.domain.AccountTransaction;
import org.brite.banking.domain.BankAddress;
import org.brite.banking.domain.BankLocations;
import org.brite.banking.domain.BankStatement;
import org.brite.banking.domain.Customer;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.TransactionRepository;
import org.brite.banking.request.AccountLookupRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.service.AccountStatusStatementService;
import org.brite.banking.service.BankStatementService;
import org.brite.banking.service.ClientAccountService;
import org.brite.banking.service.LocationBasedOperationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
    private final BankStatementService bankStatementService;
    private final LocationBasedOperationService locationService;
    private final TransactionRepository transactionRepository;
    private final PortalProperties properties;

    /**
     * The newest accounts that can be shown on the home screen - {@code ACTIVE} and {@code SUSPENDED} (closed ones
     * are omitted), newest first, at most {@code homeAccountLimit} - plus nearby locations; {@code state} is optional.
     * The account search takes one status at a time, so each status is queried (both hit the account-search cache)
     * and the two short lists are merged.
     */
    public PortalHomeResponse home(String state, Long customerId) {
        log.debug(BankingMessages.LOG_PORTAL_HOME, state);
        Page<AccountStatusView> active = recentAccounts(AccountStatus.ACTIVE, customerId);
        Page<AccountStatusView> suspended = recentAccounts(AccountStatus.SUSPENDED, customerId);
        List<PortalAccountSummary> accounts = Stream.concat(active.getContent().stream(), suspended.getContent().stream())
                .sorted(Comparator.comparing(AccountStatusView::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(properties.getHomeAccountLimit())
                .map(this::toSummary)
                .toList();
        return new PortalHomeResponse(active.getTotalElements(), suspended.getTotalElements(), accounts, nearbyLocations(state));
    }

    private Page<AccountStatusView> recentAccounts(AccountStatus status, Long customerId) {
        return accountStatusStatementService.listAccountStatuses(
                null, status, null, null, null, null, null, customerId,
                PageRequest.of(0, properties.getHomeAccountLimit(), Sort.by(Sort.Direction.DESC, "createdDate")));
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

    /**
     * The response for an account that has just been opened (by the staff portal, after the banking staff service created it): the new account's
     * overview plus branches/ATMs in {@code state}. Creating the account, with all its rules and notifications, is not done here.
     */
    public OpenAccountResponse openAccountResponse(Account account, String state) {
        log.info(BankingMessages.LOG_PORTAL_ACCOUNT_OPENED, state);
        return new OpenAccountResponse(
                toOverview(account, properties.getDefaultActivityDays(), List.of()),
                nearbyLocations(state));
    }

    /**
     * Withdraws through {@link ClientAccountService} (which enforces closed/suspended/balance rules and sends its
     * usual notifications) and returns the refreshed overview, so the portal can redraw balance and activity.
     *
     * @throws org.brite.banking.exception.AccountSuspendedException (400) if the account is suspended
     */
    public AccountOverviewResponse withdraw(WithdrawalRequest request) {
        clientAccountService.withdrawAndSaveToAccount(request);
        return overview(request.getAccountNumber(), null);
    }

    /** Deposits through {@link ClientAccountService} and returns the refreshed overview. */
    public AccountOverviewResponse deposit(DepositForm request) {
        clientAccountService.depositAndSaveToAccount(request);
        return overview(request.getAccountNumber(), null);
    }

    /**
     * Closes the account through {@link ClientAccountService} (irreversible; clears any suspension) and returns the
     * refreshed overview.
     *
     * @throws org.brite.banking.exception.AccountClosedException (400) if it is already closed
     */
    public AccountOverviewResponse close(String accountNumber) {
        clientAccountService.closeAccount(accountNumber);
        return overview(accountNumber, null);
    }

    /**
     * Generates the statement through {@link BankStatementService}. Unlike the other portal reads this has a side
     * effect (the banking service emails/SMSes the statement), so the controller exposes it as a {@code POST}.
     */
    public BankStatement statement(String accountNumber, LocalDate beginDate, LocalDate endDate) {
        log.debug(BankingMessages.LOG_PORTAL_STATEMENT, accountNumber, beginDate, endDate);
        return bankStatementService.generateStatement(accountNumber, beginDate, endDate);
    }

    private List<PortalLocation> nearbyLocations(String state) {
        return locationService.listLocations(null, null, state, null, null,
                        PageRequest.of(0, properties.getNearbyLocationLimit(), Sort.by("name")))
                .getContent().stream().map(this::toLocation).toList();
    }

    private PortalAccountSummary toSummary(AccountStatusView v) {
        return new PortalAccountSummary(v.getAccountNumber(), v.getAccountType(), v.getAccountStatus(),
                v.isSuspended(), v.getSuspendedEnd(), v.getCreatedDate(), v.getClosedDate(), v.getFirstName(), v.getLastName());
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
                c == null ? null : maskPhone(c.getPhoneNumber()),
                days, activity);
    }

    /** {@code 512-555-0101} -> {@code ***-***-0101}; null/blank stays null. The BFF never returns a full phone number. */
    public static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String digits = phoneNumber.replaceAll("\\D", "");
        return digits.length() < 4 ? "***" : "***-***-" + digits.substring(digits.length() - 4);
    }

    private PortalActivityItem toActivityItem(AccountTransaction t) {
        return new PortalActivityItem(t.getTransactionType(), t.getAmount(), t.getBalanceAfter(),
                t.getTransactionDate(), t.getDepositType(),
                t.getBankLocationName(), t.getBankLocationType(), t.getBankLocationCity(), t.getBankLocationState());
    }

    /**
     * One branch/ATM as a portal card, or null for a null id (an area manager has no branch).
     *
     * @throws org.brite.banking.exception.LocationNotFoundException (mapped to 404) if the id doesn't exist
     */
    public PortalLocation location(Long id) {
        return id == null ? null : toLocation(locationService.getLocation(id));
    }

    private PortalLocation toLocation(BankLocations l) {
        BankAddress a = l.getBankAddress();
        return new PortalLocation(l.getId(), l.getName(), l.getLocationType(),
                a == null ? null : a.getAddressLine1(), a == null ? null : a.getCity(),
                a == null ? null : a.getState(), a == null ? null : a.getZip(),
                l.getOpensAt(), l.getClosesAt(), l.getTimeZone(), l.getPhoneNumber(), l.getServices());
    }
}
