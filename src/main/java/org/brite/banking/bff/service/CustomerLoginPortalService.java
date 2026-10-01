package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.service.LoginService;
import org.brite.banking.service.StaffLoginService;
import org.springframework.stereotype.Service;

/**
 * Customer logins as the BFF serves them: the customer's own sign-in (token + home screen in one call) and a manager creating a customer's
 * login in the staff portal. No rules of its own: credential checks, lockout, status and token issuing stay in {@link LoginService}, and the
 * privilege check and creation rules in {@link StaffLoginService}; a failed login never builds the home screen.
 */
@Service
@RequiredArgsConstructor
public class CustomerLoginPortalService {
    private final LoginService loginService;
    private final PortalOrchestrationService portalService;
    private final StaffLoginService staffLoginService;

    /** Verifies the login, issues the token and returns it with the home screen ({@code state} optionally narrows nearby branches). */
    public PortalLoginResponse login(String username, String password, String state) {
        CustomerLoginResponse login = loginService.customerLogin(username, password);
        PortalHomeResponse home = portalService.home(state, login.getCustomer().getCustomerId());
        return new PortalLoginResponse(login.getAccessToken(), login.getTokenType(), login.getExpiresIn(), login.getCustomer(), home);
    }

    /** Staff portal: a manager creates a customer's login (needs MANAGE_CUSTOMER_LOGINS, checked by the banking service first). */
    public LoginStatusView createLogin(String employee, Long customerId, LoginRequest request) {
        return staffLoginService.createCustomerLogin(employee, customerId, request);
    }
}
