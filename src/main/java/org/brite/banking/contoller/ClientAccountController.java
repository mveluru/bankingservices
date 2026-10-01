package org.brite.banking.contoller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.BankStatement;
import org.brite.banking.domain.BulkCloseAccountsResult;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.request.AccountLookupRequest;
import org.brite.banking.request.AccountRegistrationRequest;
import org.brite.banking.request.BulkCloseAccountsRequest;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.service.BankStatementService;
import org.brite.banking.service.CustomerAccessService;
import org.brite.banking.service.ClientAccountService;
import org.brite.banking.service.AccountStatusStatementService;
import org.brite.banking.service.AccountSuspensionService;
import org.brite.banking.domain.AccountStatusView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Customer-facing account endpoints. Every one except {@code POST /newaccount} needs {@code Authorization: Bearer <customer token>}
 * ({@link CustomerAuthenticationFilter}), and a customer can only reach their own accounts ({@link CustomerAccessService}):
 * another customer's account is {@code 403}, and the account list shows only the caller's accounts.
 */
@RestController
@RequestMapping("/v1/api/accounts")
@RequiredArgsConstructor
public class ClientAccountController {
    private final ClientAccountService accountService;
    private final BankStatementService bankStatementService;
    private final AccountStatusStatementService accountStatusStatementService;
    private final AccountSuspensionService accountSuspensionService;
    private final CustomerAccessService customerAccess;

    /**
     * Scenario G: Retrieve account ids/details within a createdDate/closedDate range.
     * If neither {@code createdFrom} nor {@code createdTo} is given, defaults to
     * "as of today minus {@code months} months" (18 months if {@code months} is also
     * omitted); supplying either explicit created-date bound disables that default and
     * {@code months} is ignored. Conditional lookup: if {@code accountNumber} is
     * provided, only that account is returned (still subject to the resolved
     * date-range/status filters); if it's omitted/null, every matching account is
     * returned, paginated.
     * GET /api/accounts?months=6
     * GET /api/accounts?accountNumber=CH-0000088291&status=CLOSED&createdFrom=2021-01-01&createdTo=2021-12-31&page=0&size=20&sort=createdDate,desc
     */
    @GetMapping
    public ResponseEntity<Page<AccountStatusView>> listAccounts(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closedTo,
            @RequestParam(required = false) Integer months,
            @PageableDefault(size = 20, sort = "createdDate") Pageable pageable) {
        Page<AccountStatusView> accounts = accountStatusStatementService.listAccountStatuses(
                accountNumber, status, createdFrom, createdTo, closedFrom, closedTo, months,
                customerAccess.requireAuthenticated(customerId), pageable);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Scenario A: Lookup customer profile information by Account Number
     * POST /api/accounts/lookup
     */
    @PostMapping("/lookup")
    public ResponseEntity<?> lookupAccount(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                           @Valid @RequestBody AccountLookupRequest request) {
        customerAccess.requireOwnAccount(customerId, request.getAccountNumber());
        return accountService.lookupAccountDetails(request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("Account number not found in our records."));
    }

    /**
     * Scenario B: Create a brand new account and customer record structure
     * POST /api/accounts/newaccount
     */
    @PostMapping("/newaccount")
    public ResponseEntity<Account> registerAccount(@Valid @RequestBody AccountRegistrationRequest request) {
        Account createdAccount = accountService.registerNewClientAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAccount);
    }

    /**
     * Scenario C: Withdraw funds from an existing checking or savings account
     * POST /api/accounts/withdraw
     */
    @PostMapping("/withdraw")
    public ResponseEntity<Account> withdraw(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                            @Valid @RequestBody WithdrawalRequest request) {
        customerAccess.requireOwnAccount(customerId, request.getAccountNumber());
        Account updatedAccount = accountService.withdrawAndSaveToAccount(request);
        return ResponseEntity.ok(updatedAccount);
    }

    /**
     * Scenario D: Deposit funds into an existing checking or savings account
     * POST /api/accounts/deposit
     */
    @PostMapping("/deposit")
    public ResponseEntity<Account> deposit(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                           @Valid @RequestBody DepositForm request) {
        customerAccess.requireOwnAccount(customerId, request.getAccountNumber());
        Account updatedAccount = accountService.depositAndSaveToAccount(request);
        return ResponseEntity.ok(updatedAccount);
    }

    /**
     * Scenario F: Close an existing checking or savings account
     * POST /api/accounts/{accountNumber}/close
     */
    @PostMapping("/{accountNumber}/close")
    public ResponseEntity<Account> closeAccount(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                @PathVariable String accountNumber) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        Account closedAccount = accountService.closeAccount(accountNumber);
        return ResponseEntity.ok(closedAccount);
    }

    /**
     * Scenario J: Suspend an account. While suspended it rejects every withdraw/deposit (400)
     * until reactivated or {@code endDateTime} passes. {@code startDateTime} defaults to now.
     * POST /api/accounts/{accountNumber}/suspend
     */
    @PostMapping("/{accountNumber}/suspend")
    public ResponseEntity<Account> suspendAccount(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                  @PathVariable String accountNumber,
                                                  @Valid @RequestBody SuspendAccountRequest request) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        return ResponseEntity.ok(accountSuspensionService.suspendAccount(accountNumber, request));
    }

    /**
     * Scenario K: Change the end and/or notes of a current suspension (only supplied fields change).
     * PATCH /api/accounts/{accountNumber}/suspension
     */
    @PatchMapping("/{accountNumber}/suspension")
    public ResponseEntity<Account> updateSuspension(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                    @PathVariable String accountNumber,
                                                    @Valid @RequestBody UpdateSuspensionRequest request) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        return ResponseEntity.ok(accountSuspensionService.updateSuspension(accountNumber, request));
    }

    /**
     * Scenario L: Lift a suspension, returning the account to ACTIVE so it can transact again.
     * POST /api/accounts/{accountNumber}/reactivate
     */
    @PostMapping("/{accountNumber}/reactivate")
    public ResponseEntity<Account> reactivateAccount(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                     @PathVariable String accountNumber) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        return ResponseEntity.ok(accountSuspensionService.reactivateAccount(accountNumber));
    }

    /**
     * Scenario I: Bulk-close multiple accounts by number in one call. Best-effort - an
     * invalid or already-closed account number doesn't block the others; the response
     * carries both the accounts that were closed and any per-account failures.
     * POST /api/accounts/close
     */
    @PostMapping("/close")
    public ResponseEntity<BulkCloseAccountsResult> closeAccounts(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                                 @Valid @RequestBody BulkCloseAccountsRequest request) {
        customerAccess.requireOwnAccounts(customerId, request.getAccountNumbers());
        BulkCloseAccountsResult result = accountService.closeAccounts(request.getAccountNumbers());
        return ResponseEntity.ok(result);
    }

    /**
     * Scenario E: Generate a bank statement for an account within a date range
     * GET /api/accounts/{accountNumber}/statement?beginDate=yyyy-MM-dd&endDate=yyyy-MM-dd
     */
    @GetMapping("/{accountNumber}/statement")
    public ResponseEntity<BankStatement> statement(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        BankStatement statement = bankStatementService.generateStatement(accountNumber, beginDate, endDate);
        return ResponseEntity.ok(statement);
    }
}
