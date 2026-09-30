package org.bee.banking.bff.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bee.banking.bff.dto.AccountOverviewResponse;
import org.bee.banking.bff.dto.OpenAccountResponse;
import org.bee.banking.bff.dto.PortalHomeResponse;
import org.bee.banking.bff.service.PortalOrchestrationService;
import org.bee.banking.request.AccountRegistrationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
     * Open an account and get nearby branches in the same call.
     * POST /bff/v1/portal/accounts/open
     */
    @PostMapping("/accounts/open")
    public ResponseEntity<OpenAccountResponse> openAccount(@Valid @RequestBody AccountRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(portalService.openAccount(request));
    }
}
