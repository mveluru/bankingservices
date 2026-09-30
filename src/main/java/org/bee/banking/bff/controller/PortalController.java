package org.bee.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bee.banking.bff.dto.AccountOverviewResponse;
import org.bee.banking.bff.dto.OpenAccountResponse;
import org.bee.banking.bff.dto.PortalHomeResponse;
import org.bee.banking.bff.service.PortalOrchestrationService;
import org.bee.banking.domain.DepositForm;
import org.bee.banking.request.AccountRegistrationRequest;
import org.bee.banking.request.SuspendAccountRequest;
import org.bee.banking.request.UpdateSuspensionRequest;
import org.bee.banking.request.WithdrawalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Backend-for-frontend endpoints for the banking UI portal: one call per screen. */
@RestController
@RequestMapping("/bff/v1/portal")
@RequiredArgsConstructor
public class PortalController {
    private final PortalOrchestrationService portalService;

    /**
     * Home screen: active accounts plus branches/ATMs (optionally limited to a state).
     * GET /bff/v1/portal/home?state=TX
     */
    @GetMapping("/home")
    public ResponseEntity<PortalHomeResponse> home(@RequestParam(required = false) String state) {
        return ResponseEntity.ok(portalService.home(state));
    }

    /**
     * Account detail: balance plus recent activity for the last {@code days} days.
     * GET /bff/v1/portal/accounts/CH-0000088291/overview?days=30
     */
    @GetMapping("/accounts/{accountNumber}/overview")
    public ResponseEntity<AccountOverviewResponse> overview(@PathVariable String accountNumber,
                                                            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(portalService.overview(accountNumber, days));
    }

    /**
     * Withdraw and get the refreshed overview. {@code 400} if the account is closed or suspended.
     * POST /bff/v1/portal/accounts/withdraw
     */
    @PostMapping("/accounts/withdraw")
    public ResponseEntity<AccountOverviewResponse> withdraw(@Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(portalService.withdraw(request));
    }

    /**
     * Deposit and get the refreshed overview. {@code 400} if the account is closed or suspended.
     * POST /bff/v1/portal/accounts/deposit
     */
    @PostMapping("/accounts/deposit")
    public ResponseEntity<AccountOverviewResponse> deposit(@Valid @RequestBody DepositForm request) {
        return ResponseEntity.ok(portalService.deposit(request));
    }

    /**
     * Suspend an account; returns the refreshed overview (`suspended: true`, `suspendedUntil`).
     * POST /bff/v1/portal/accounts/CH-0000010001/suspend
     */
    @PostMapping("/accounts/{accountNumber}/suspend")
    public ResponseEntity<AccountOverviewResponse> suspend(@PathVariable String accountNumber,
                                                           @Valid @RequestBody SuspendAccountRequest request) {
        return ResponseEntity.ok(portalService.suspend(accountNumber, request));
    }

    /**
     * Change a current suspension's end and/or notes; returns the refreshed overview.
     * PATCH /bff/v1/portal/accounts/CH-0000010001/suspension
     */
    @PatchMapping("/accounts/{accountNumber}/suspension")
    public ResponseEntity<AccountOverviewResponse> updateSuspension(@PathVariable String accountNumber,
                                                                    @Valid @RequestBody UpdateSuspensionRequest request) {
        return ResponseEntity.ok(portalService.updateSuspension(accountNumber, request));
    }

    /**
     * Lift a suspension; returns the refreshed overview.
     * POST /bff/v1/portal/accounts/CH-0000010001/reactivate
     */
    @PostMapping("/accounts/{accountNumber}/reactivate")
    public ResponseEntity<AccountOverviewResponse> reactivate(@PathVariable String accountNumber) {
        return ResponseEntity.ok(portalService.reactivate(accountNumber));
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
