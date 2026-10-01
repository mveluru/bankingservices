package org.brite.banking.bff;

import org.brite.banking.bff.dto.PortalHomeResponse;
import org.brite.banking.bff.dto.PortalLoginResponse;
import org.brite.banking.bff.service.CustomerLoginPortalService;
import org.brite.banking.bff.service.PortalOrchestrationService;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.CustomerLoginResponse;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.request.LoginRequest;
import org.brite.banking.service.LoginService;
import org.brite.banking.service.StaffLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test: the customer-login BFF service only composes / delegates to the banking login services. */
class CustomerLoginPortalServiceTest {
    private LoginService loginService;
    private PortalOrchestrationService portalService;
    private StaffLoginService staffLoginService;
    private CustomerLoginPortalService service;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        portalService = mock(PortalOrchestrationService.class);
        staffLoginService = mock(StaffLoginService.class);
        service = new CustomerLoginPortalService(loginService, portalService, staffLoginService);
    }

    @Test
    void loginReturnsTheTokenTheCustomerAndTheirHomeScreenInOneResponse() {
        AuthenticatedCustomer customer = AuthenticatedCustomer.builder().customerId(5L).firstName("Alice").lastName("Smith").build();
        when(loginService.customerLogin("customer0005", "20260005")).thenReturn(
                CustomerLoginResponse.builder().accessToken("signed.jwt.value").expiresIn(1800).customer(customer).build());
        PortalHomeResponse home = new PortalHomeResponse(1, 0, List.of(), List.of());
        when(portalService.home("TX", 5L)).thenReturn(home);

        PortalLoginResponse response = service.login("customer0005", "20260005", "TX");

        assertEquals("signed.jwt.value", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(customer, response.customer());
        assertSame(home, response.home());
        assertFalse(response.toString().contains("signed.jwt.value"), "the token must not appear in toString");
    }

    @Test
    void aFailedLoginPropagatesAndNeverBuildsTheHomeScreen() {
        when(loginService.customerLogin("customer0005", "00000000")).thenThrow(new InvalidCredentialsException("Invalid username or password"));

        assertThrows(InvalidCredentialsException.class, () -> service.login("customer0005", "00000000", null));
        verifyNoInteractions(portalService);
    }

    @Test
    void creatingACustomerLoginDelegatesToTheBankingStaffServiceWhichChecksThePrivilege() {
        LoginRequest request = new LoginRequest("alice.smith", "13572468");
        when(staffLoginService.createCustomerLogin("EMP-M", 11L, request))
                .thenReturn(LoginStatusView.builder().username("alice.smith").status(LoginStatus.ACTIVE).build());
        when(staffLoginService.createCustomerLogin("EMP-T", 11L, request)).thenThrow(new EmployeeNotAuthorizedException("not authorized"));

        assertEquals("alice.smith", service.createLogin("EMP-M", 11L, request).getUsername());
        assertThrows(EmployeeNotAuthorizedException.class, () -> service.createLogin("EMP-T", 11L, request));
        verifyNoInteractions(loginService);
    }
}
