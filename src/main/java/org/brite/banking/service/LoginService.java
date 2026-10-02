package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.IssuedToken;
import org.brite.banking.domain.StaffLoginResponse;
import org.springframework.stereotype.Service;

/**
 * Verifies the credentials and, only if they are valid, issues the access token. All the credential rules
 * (password format, lockout, login status) stay in the credential services; any failure they throw propagates
 * and no token is issued. A successful customer login is also counted for the day ({@link CustomerQuotaService}).
 */
@Service
@RequiredArgsConstructor
public class LoginService {
    private final EmployeeCredentialService employeeCredentialService;
    private final CustomerCredentialService customerCredentialService;
    private final JwtService jwtService;
    private final CustomerQuotaService customerQuotaService;

    public StaffLoginResponse staffLogin(String username, String password) {
        Employee employee = employeeCredentialService.verify(username, password);
        IssuedToken token = jwtService.issueEmployeeToken(employee);
        return StaffLoginResponse.builder()
                .accessToken(token.getToken()).expiresIn(token.getExpiresInSeconds()).employee(employee).build();
    }

    public CustomerLoginResponse customerLogin(String username, String password) {
        AuthenticatedCustomer customer = customerCredentialService.verify(username, password);
        IssuedToken token = jwtService.issueCustomerToken(customer);
        customerQuotaService.recordLogin(customer.getCustomerId());
        return CustomerLoginResponse.builder()
                .accessToken(token.getToken()).expiresIn(token.getExpiresInSeconds()).customer(customer).build();
    }
}
