package org.brite.banking.bff.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.request.ChangeLoginStatusRequest;
import org.brite.banking.service.StaffLoginService;
import org.springframework.stereotype.Service;

/**
 * Staff portal: setting a customer's login status (ACTIVE, INACTIVE, LOCKED, SUSPENDED; only ACTIVE may transact). A pure delegate: the
 * privilege check (MANAGE_CUSTOMER_LOGINS) and the status rules live in {@link StaffLoginService} and run before anything changes.
 */
@Service
@RequiredArgsConstructor
public class CustomerLoginStatusPortalService {
    private final StaffLoginService staffLoginService;

    public LoginStatusView changeStatus(String employee, Long customerId, ChangeLoginStatusRequest request) {
        return staffLoginService.changeCustomerLoginStatus(employee, customerId, request);
    }
}
