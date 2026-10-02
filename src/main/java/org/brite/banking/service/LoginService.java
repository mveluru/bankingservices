package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.IssuedToken;
import org.brite.banking.domain.StaffLoginResponse;
import org.brite.banking.exception.AccountHolderLoginBlockedException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Verifies the credentials and, only if they are valid, issues the access token. All the credential rules
 * (password format, lockout, login status) stay in the credential services; any failure they throw propagates
 * and no token is issued. A successful login is also counted for the day ({@link CustomerQuotaService}, {@link EmployeeQuotaService}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoginService {
    private final EmployeeCredentialService employeeCredentialService;
    private final CustomerCredentialService customerCredentialService;
    private final JwtService jwtService;
    private final CustomerQuotaService customerQuotaService;
    private final EmployeeQuotaService employeeQuotaService;
    private final AccountRepository accountRepository;

    public StaffLoginResponse staffLogin(String username, String password) {
        Employee employee = employeeCredentialService.verify(username, password);
        IssuedToken token = jwtService.issueEmployeeToken(employee);
        employeeQuotaService.recordLogin(employee.getEmployeeNumber());
        return StaffLoginResponse.builder()
                .accessToken(token.getToken()).expiresIn(token.getExpiresInSeconds()).employee(employee).build();
    }

    public CustomerLoginResponse customerLogin(String username, String password) {
        AuthenticatedCustomer customer = customerCredentialService.verify(username, password);
        requireAnActiveAccount(customer.getCustomerId());
        IssuedToken token = jwtService.issueCustomerToken(customer);
        customerQuotaService.recordLogin(customer.getCustomerId());
        return CustomerLoginResponse.builder()
                .accessToken(token.getToken()).expiresIn(token.getExpiresInSeconds()).customer(customer).build();
    }

    /**
     * An account holder can sign in only while at least one of their accounts is ACTIVE. When every account is SUSPENDED, CLOSED, INACTIVE or
     * DORMANT, sign-in is refused (403) with a message that names the status and says to contact customer support. This runs after the
     * password is verified, so the account status is never revealed to someone who only guessed a username. A customer with no accounts at all is
     * not blocked. It is a sign-in rule only: a token issued earlier keeps working until it expires, and each blocked account still rejects its own
     * withdrawals and deposits.
     *
     * @throws AccountHolderLoginBlockedException (mapped to 403) when no account is ACTIVE
     */
    private void requireAnActiveAccount(Long customerId) {
        List<AccountStatus> statuses = accountRepository.findStatusesByCustomerId(customerId);
        if (statuses.isEmpty() || statuses.contains(AccountStatus.ACTIVE)) {
            return;
        }
        String shown = statuses.stream().distinct().sorted().map(Enum::name).collect(Collectors.joining(", "));
        log.warn(BankingMessages.LOG_LOGIN_BLOCKED_BY_ACCOUNT_STATUS, customerId, shown);
        throw new AccountHolderLoginBlockedException(String.format(BankingMessages.LOGIN_BLOCKED_BY_ACCOUNT_STATUS, shown));
    }
}
