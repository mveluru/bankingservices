package org.brite.banking.contoller;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.repository.EmployeeRepository;
import org.brite.banking.service.AccountSuspensionService;
import org.brite.banking.service.ClientAccountService;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.StaffAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clientAccountService = mock(ClientAccountService.class);
        suspensionService = mock(AccountSuspensionService.class);
        employeeRepository = mock(EmployeeRepository.class);
        EmployeeService employeeService = new EmployeeService(employeeRepository);
        StaffAccountService staffAccountService = new StaffAccountService(employeeService, clientAccountService, suspensionService);
        mockMvc = MockMvcBuilders.standaloneSetup(new StaffController(staffAccountService, employeeService))
                .setControllerAdvice(new BankingExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
        when(clientAccountService.depositAndSaveToAccount(any())).thenReturn(account());
        when(suspensionService.suspendAccount(eq(ACCOUNT), any())).thenReturn(account());
        when(suspensionService.reactivateAccount(ACCOUNT)).thenReturn(account());
        when(clientAccountService.closeAccount(ACCOUNT)).thenReturn(account());
    }

    private Account account() {
        return Account.builder().checkingAccountNumber(ACCOUNT).accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE).build();
    }

    private void employee(String number, EmployeeRole role, EmployeeStatus status) {
        when(employeeRepository.findByEmployeeNumber(number)).thenReturn(Optional.of(
                Employee.builder().employeeNumber(number).firstName("Test").lastName(number).role(role).status(status).build()));
    }

    @Test
    void tellerCanDeposit() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(post("/v1/api/staff/accounts/deposit").header("X-Employee-Number", "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT_BODY))
                .andExpect(status().isOk());
        verify(clientAccountService).depositAndSaveToAccount(any());
    }

    @Test
    void tellerCannotSuspendReactivateOrCloseAndNoAccountIsTouched() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/suspend").header("X-Employee-Number", "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(SUSPEND_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Employee EMP-T (TELLER) is not authorized: requires SUSPEND_ACCOUNT"));
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/reactivate").header("X-Employee-Number", "EMP-T"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").header("X-Employee-Number", "EMP-T"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(suspensionService);
        verify(clientAccountService, never()).closeAccount(any());
    }

    @Test
    void managerCanSuspendReactivateAndClose() throws Exception {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/suspend").header("X-Employee-Number", "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content(SUSPEND_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/reactivate").header("X-Employee-Number", "EMP-M"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").header("X-Employee-Number", "EMP-M"))
                .andExpect(status().isOk());
    }

    @Test
    void updateSuspensionNeedsItsOwnPrivilege() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(patch("/v1/api/staff/accounts/" + ACCOUNT + "/suspension").header("X-Employee-Number", "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(suspensionService);
    }

    @Test
    void onLeaveManagerIsForbidden() throws Exception {
        employee("EMP-L", EmployeeRole.MANAGER, EmployeeStatus.ON_LEAVE);
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").header("X-Employee-Number", "EMP-L"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Employee EMP-L is ON_LEAVE and cannot perform this action"));
    }

    @Test
    void missingHeaderIs400AndUnknownEmployeeIs404() throws Exception {
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Header X-Employee-Number with the acting employee number is required"));
        when(employeeRepository.findByEmployeeNumber("EMP-NONE")).thenReturn(Optional.empty());
        mockMvc.perform(post("/v1/api/staff/accounts/" + ACCOUNT + "/close").header("X-Employee-Number", "EMP-NONE"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Employee not found: EMP-NONE"));
    }

    @Test
    void ownProfileIncludesDerivedPrivileges() throws Exception {
        employee("EMP-T", EmployeeRole.TELLER, EmployeeStatus.ACTIVE);
        mockMvc.perform(get("/v1/api/staff/employees/EMP-T").header("X-Employee-Number", "EMP-T"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNumber").value("EMP-T"))
                .andExpect(jsonPath("$.privileges.length()").value(3));
    }

    @Test
    void listingEmployeesIsAreaManagerOnly() throws Exception {
        employee("EMP-M", EmployeeRole.MANAGER, EmployeeStatus.ACTIVE);
        mockMvc.perform(get("/v1/api/staff/employees").header("X-Employee-Number", "EMP-M"))
                .andExpect(status().isForbidden());
    }
}
