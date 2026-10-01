package org.brite.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.dto.OpenAccountResponse;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.service.PortalOrchestrationService;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.service.CustomerAccessService;
import org.brite.banking.domain.BankStatement;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.request.AccountRegistrationRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Backend-for-frontend endpoints for the banking UI portal: one call per screen. Every one except {@code POST accounts/open}
 * needs {@code Authorization: Bearer <customer token>} ({@link CustomerAuthenticationFilter}) and only reaches the caller's
 * own accounts ({@link CustomerAccessService}; another customer's account is {@code 403}, the home screen lists only the caller's). Suspending and reactivating are staff-only and not offered here.
 */
@RestController
@RequestMapping("/bff/v1/portal")
@RequiredArgsConstructor
public class PortalController {
    private final PortalOrchestrationService portalService;
    private final CustomerAccessService customerAccess;

    /**
     * Home screen: active accounts plus branches/ATMs (optionally limited to a state).
     * GET /bff/v1/portal/home?state=TX
     */
    @GetMapping("/home")
    public ResponseEntity<PortalHomeResponse> home(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                   @RequestParam(required = false) String state) {
        return ResponseEntity.ok(portalService.home(state, customerAccess.requireAuthenticated(customerId)));
    }

    /**
     * Account detail: balance plus recent activity for the last {@code days} days.
     * GET /bff/v1/portal/accounts/CH-0000088291/overview?days=30
     */
    @GetMapping("/accounts/{accountNumber}/overview")
    public ResponseEntity<AccountOverviewResponse> overview(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                            @PathVariable String accountNumber,
                                                            @RequestParam(required = false) Integer days) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        return ResponseEntity.ok(portalService.overview(accountNumber, days));
    }

    /**
     * Withdraw and get the refreshed overview. {@code 400} if the account is closed or suspended.
     * POST /bff/v1/portal/accounts/withdraw
     */
    @PostMapping("/accounts/withdraw")
    public ResponseEntity<AccountOverviewResponse> withdraw(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                            @Valid @RequestBody WithdrawalRequest request) {
        customerAccess.requireOwnAccount(customerId, request.getAccountNumber());
        return ResponseEntity.ok(portalService.withdraw(request));
    }

    /**
     * Deposit and get the refreshed overview. {@code 400} if the account is closed or suspended.
     * POST /bff/v1/portal/accounts/deposit
     */
    @PostMapping("/accounts/deposit")
    public ResponseEntity<AccountOverviewResponse> deposit(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                           @Valid @RequestBody DepositForm request) {
        customerAccess.requireOwnAccount(customerId, request.getAccountNumber());
        return ResponseEntity.ok(portalService.deposit(request));
    }

    /**
     * Close an account (irreversible); returns the refreshed overview ({@code CLOSED}, {@code closedDate}).
     * POST /bff/v1/portal/accounts/CH-0000010001/close
     */
    @PostMapping("/accounts/{accountNumber}/close")
    public ResponseEntity<AccountOverviewResponse> close(@RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
                                                         @PathVariable String accountNumber) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        return ResponseEntity.ok(portalService.close(accountNumber));
    }

    /**
     * Statement for a date range. A {@code POST} because it has a side effect: the banking service emails/SMSes the
     * statement, and portal reads must be side-effect free.
     * POST /bff/v1/portal/accounts/CH-0000088291/statement?beginDate=2026-08-01&endDate=2026-09-24
     */
    @PostMapping("/accounts/{accountNumber}/statement")
    public ResponseEntity<BankStatement> statement(
            @RequestAttribute(value = CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, required = false) Long customerId,
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        customerAccess.requireOwnAccount(customerId, accountNumber);
        return ResponseEntity.ok(portalService.statement(accountNumber, beginDate, endDate));
    }

    /**
     * Open an account and get nearby branches in the same call.
     * POST /bff/v1/portal/accounts/open
     */
    @PostMapping("/accounts/open")
    public ResponseEntity<OpenAccountResponse> openAccount(@Valid @RequestBody AccountRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(portalService.openAccount(request));
    }
}
