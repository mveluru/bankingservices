package org.brite.banking.contoller;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.BankAddress;
import org.brite.banking.domain.BankLocations;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.LocationType;
import org.brite.banking.domain.TransactionHandler;
import org.brite.banking.exception.LocationNotFoundException;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.domain.CustomerCredential;
import org.brite.banking.domain.EmployeeCredential;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.exception.CustomerNotFoundException;
import org.brite.banking.repository.EmployeeCredentialRepository;
import org.brite.banking.repository.EmployeeRepository;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.StaffLoginService;
import org.brite.banking.service.AccountSuspensionService;
import org.brite.banking.service.ClientAccountService;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.LocationBasedOperationService;
import org.brite.banking.service.StaffAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc (no Spring context, no MySQL) with the real EmployeeService/StaffAccountService
 * over a mocked repository and mocked account services: proves the role rules reach HTTP status
 * codes and that a rejected employee never touches an account.
 */
class StaffControllerTest {
    private static final String ACCOUNT = "CH-0000010001";
    private static final String DEPOSIT_BODY = """
            {"AccountNumber":"CH-0000010001","amount":50,"accountType":"CHECKING","depositType":"check",
             "street":"1 Main St","addressLine1":"1 Main St","city":"Austin","state":"TX","zip":"78701"}""";
    private static final String SUSPEND_BODY = "{\"notes\":\"Fraud review\"}";

    private ClientAccountService clientAccountService;
    private AccountSuspensionService suspensionService;
    private EmployeeRepository employeeRepository;
    private LocationBasedOperationService locationService;
    private EmployeeCredentialRepository employeeCredentials;
    private EmployeeCredentialService employeeCredentialService;
    private CustomerCredentialService customerCredentialService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clientAccountService = mock(ClientAccountService.class);
        suspensionService = mock(AccountSuspensionService.class);
        employeeRepository = mock(EmployeeRepository.class);
        locationService = mock(LocationBasedOperationService.class);
        employeeCredentials = mock(EmployeeCredentialRepository.class);
        employeeCredentialService = mock(EmployeeCredentialService.class);
        customerCredentialService = mock(CustomerCredentialService.class);
        when(employeeCredentials.findByEmployeeId(any())).thenReturn(Optional.of(
                EmployeeCredential.builder().username("u").status(LoginStatus.ACTIVE).build()));
        EmployeeService employeeService = new EmployeeService(employeeRepository, employeeCredentials);
        StaffLoginService staffLoginService = new StaffLoginService(employeeService, employeeCredentialService, customerCredentialService);
        StaffAccountService staffAccountService = new StaffAccountService(employeeService, clientAccountService, suspensionService, locationService);
        mockMvc = MockMvcBuilders.standaloneSetup(new StaffController(staffAccountService, employeeService, staffLoginService))
                .setControllerAdvice(new BankingExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
        when(clientAccountService.depositAndSaveToAccount(any(), any())).thenReturn(account());
        when(clientAccountService.withdrawAndSaveToAccount(any(), any())).thenReturn(account());
        when(locationService.getLocation(1L)).thenReturn(location(1L, "Austin Downtown Branch", LocationType.OFFICE, "Austin", "TX"));
        when(locationService.getLocation(4L)).thenReturn(location(4L, "San Antonio Riverwalk ATM", LocationType.ATM, "San Antonio", "TX"));
        when(locationService.getLocation(999L)).thenThrow(new LocationNotFoundException("Bank location not found: 999"));
        when(suspensionService.suspendAccount(eq(ACCOUNT), any())).thenReturn(account());
        when(suspensionService.reactivateAccount(ACCOUNT)).thenReturn(account());
        when(clientAccountService.closeAccount(ACCOUNT)).thenReturn(account());
    }

    private Account account() {
        return Account.builder().checkingAccountNumber(ACCOUNT).accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE).build();
    }

    private void employee(String number, EmployeeRole role, EmployeeStatus status) {
        employee(number, role, status, null);
    }

    private void employee(String number, EmployeeRole role, EmployeeStatus status, Long bankLocationId) {
        when(employeeRepository.findByEmployeeNumber(number)).thenReturn(Optional.of(
                Employee.builder().id(1L).employeeNumber(number).firstName("Test").lastName(number).role(role).status(status)
                        .bankLocationId(bankLocationId).build()));
    }

    private BankLocations location(Long id, String name, LocationType type, String city, String state) {
        return BankLocations.builder().id(id).name(name).locationType(type)
                .bankAddress(BankAddress.builder().city(city).state(state).build()).build();
    }

    private TransactionHandler capturedDepositHandler() {
        ArgumentCaptor<TransactionHandler> captor = ArgumentCaptor.forClass(TransactionHandler.class);
        verify(clientAccountService).depositAndSaveToAccount(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void tellerCanDeposit() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(post("/v1/api/staff/accounts/deposit").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT_BODY))
                .andExpect(status().isOk());
        verify(clientAccountService).depositAndSaveToAccount(any(), any());
    }

    @Test
    void tellerCannotSuspendReactivateOrCloseAndNoAccountIsTouched() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/suspend").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(SUSPEND_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Employee EMP-T (TELLER) is not authorized: requires SUSPEND_ACCOUNT"));
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/reactivate").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(suspensionService);
        verify(clientAccountService, never()).closeAccount(any());
    }

    @Test
    void managerCanSuspendReactivateAndClose() throws Exception {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/suspend").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content(SUSPEND_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/reactivate").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M"))
                .andExpect(status().isOk());
    }

    @Test
    void updateSuspensionNeedsItsOwnPrivilege() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(patch("/v1/api/staff/accounts/" + ACCOUNT + "/suspension").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(suspensionService);
    }

    @Test
    void onLeaveManagerIsForbidden() throws Exception {
        employee("EMP-L", EmployeeRole.MANAGER, EmployeeStatus.ON_LEAVE);
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-L"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Employee EMP-L is ON_LEAVE and cannot perform this action"));
    }

    @Test
    void noAuthenticatedEmployeeIs401AndUnknownEmployeeIs404() throws Exception {
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Authentication required: send 'Authorization: Bearer <token>' from POST /v1/api/staff/login"));
        when(employeeRepository.findByEmployeeNumber("EMP-NONE")).thenReturn(Optional.empty());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-NONE"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Employee not found: EMP-NONE"));
    }

    @Test
    void ownProfileIncludesDerivedPrivileges() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(get("/v1/api/staff/employees/EMP-T").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNumber").value("EMP-T"))
                .andExpect(jsonPath("$.privileges.length()").value(3));
    }

    @Test
    void listingEmployeesIsAreaManagerOnly() throws Exception {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        mockMvc.perform(get("/v1/api/staff/employees").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M"))
                .andExpect(status().isForbidden());
    }

    @Test
    void depositRecordsTheTellerAndTheirOwnBranch() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE, 1L);
        mockMvc.perform(post("/v1/api/staff/accounts/deposit").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT_BODY))
                .andExpect(status().isOk());
        TransactionHandler handler = capturedDepositHandler();
        assertEquals("EMP-T", handler.getEmployeeNumber());
        assertEquals("Test EMP-T", handler.getEmployeeName());
        assertEquals(EmployeeRole.TELLER, handler.getEmployeeRole());
        assertEquals(1L, handler.getBankLocationId());
        assertEquals("Austin Downtown Branch", handler.getBankLocationName());
        assertEquals(LocationType.OFFICE, handler.getBankLocationType());
        assertEquals("Austin", handler.getBankLocationCity());
        assertEquals("TX", handler.getBankLocationState());
    }

    @Test
    void locationIdParamRecordsAnAtmInsteadOfTheEmployeesBranch() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE, 1L);
        mockMvc.perform(post("/v1/api/staff/accounts/deposit?locationId=4").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT_BODY))
                .andExpect(status().isOk());
        TransactionHandler handler = capturedDepositHandler();
        assertEquals(4L, handler.getBankLocationId());
        assertEquals(LocationType.ATM, handler.getBankLocationType());
    }

    @Test
    void areaManagerWithoutLocationRecordsNoLocation() throws Exception {
        employee("EMP-A", EmployeeRole.AREA_MANAGER, EmployeeStatus.ACTIVE);
        mockMvc.perform(post("/v1/api/staff/accounts/deposit").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-A")
                        .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT_BODY))
                .andExpect(status().isOk());
        TransactionHandler handler = capturedDepositHandler();
        assertEquals("EMP-A", handler.getEmployeeNumber());
        assertNull(handler.getBankLocationId());
    }

    @Test
    void unknownLocationIs404AndNothingIsDeposited() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE, 1L);
        mockMvc.perform(post("/v1/api/staff/accounts/deposit?locationId=999").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Bank location not found: 999"));
        verify(clientAccountService, never()).depositAndSaveToAccount(any(), any());
    }

    @Test
    void withdrawRecordsTheEmployeeAndBranchToo() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE, 1L);
        mockMvc.perform(post("/v1/api/staff/accounts/withdraw").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"CH-0000010001\",\"accountType\":\"CHECKING\",\"withdrawAmount\":10,"
                                + "\"street\":\"1 Main St\",\"city\":\"Austin\",\"state\":\"TX\",\"zip\":\"78701\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<TransactionHandler> captor = ArgumentCaptor.forClass(TransactionHandler.class);
        verify(clientAccountService).withdrawAndSaveToAccount(any(), captor.capture());
        assertEquals("EMP-T", captor.getValue().getEmployeeNumber());
        assertEquals(1L, captor.getValue().getBankLocationId());
    }

    private void loginIs(LoginStatus status) {
        when(employeeCredentials.findByEmployeeId(any())).thenReturn(Optional.of(
                EmployeeCredential.builder().username("u").status(status).build()));
    }

    @Test
    void suspendedInactiveOrLockedEmployeeLoginCannotTransact() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE, 1L);
        for (LoginStatus status : new LoginStatus[]{LoginStatus.SUSPENDED, LoginStatus.INACTIVE, LoginStatus.LOCKED}) {
            loginIs(status);
            mockMvc.perform(post("/v1/api/staff/accounts/deposit").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T")
                            .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string("Employee EMP-T login is " + status + "; only an ACTIVE login can perform transactions"));
        }
        verifyNoInteractions(suspensionService);
        verify(clientAccountService, never()).depositAndSaveToAccount(any(), any());
    }

    @Test
    void areaManagerCanChangeAnEmployeeLoginStatus() throws Exception {
        employee("EMP-A", EmployeeRole.AREA_MANAGER, EmployeeStatus.ACTIVE);
        when(employeeCredentialService.changeStatus("EMP-000010", LoginStatus.SUSPENDED, "Audit")).thenReturn(
                LoginStatusView.builder().username("lucas.meyer").status(LoginStatus.SUSPENDED).statusReason("Audit").build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/api/staff/employees/EMP-000010/login-status")
                        .requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-A").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Audit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Audit"));
    }

    @Test
    void managerCannotChangeAnEmployeeLoginStatusButCanChangeACustomers() throws Exception {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/api/staff/employees/EMP-000010/login-status")
                        .requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(employeeCredentialService);

        when(customerCredentialService.changeStatus(5L, LoginStatus.INACTIVE, null)).thenReturn(
                LoginStatusView.builder().username("customer0005").status(LoginStatus.INACTIVE).build());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/api/staff/customers/5/login-status")
                        .requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void tellerCannotChangeACustomerLoginStatusAndAMissingStatusIsRejected() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/api/staff/customers/5/login-status")
                        .requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-T").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(customerCredentialService);

        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/api/staff/customers/5/login-status")
                        .requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCustomerLoginIs404() throws Exception {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        when(customerCredentialService.changeStatus(99L, LoginStatus.ACTIVE, null))
                .thenThrow(new CustomerNotFoundException("Customer not found: 99"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/api/staff/customers/99/login-status")
                        .requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isNotFound());
    }
}
