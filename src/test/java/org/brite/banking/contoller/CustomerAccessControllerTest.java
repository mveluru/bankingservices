package org.brite.banking.contoller;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.PortalController;
import org.brite.banking.bff.service.PortalOrchestrationService;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.service.AccountStatusStatementService;
import org.brite.banking.service.AccountSuspensionService;
import org.brite.banking.service.BankStatementService;
import org.brite.banking.service.ClientAccountService;
import org.brite.banking.service.CustomerAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc: an authenticated customer (id 5, set as the request attribute the filter would set) reaches their own
 * accounts and gets 403 on anyone else's, before any service runs. Customer 5 owns CH-MINE; customer 6 owns CH-THEIRS.
 */
class CustomerAccessControllerTest {
    private static final String DENIED = "Account CH-THEIRS does not belong to the authenticated customer";

    private ClientAccountService accountService;
    private AccountSuspensionService suspensionService;
    private AccountStatusStatementService statusService;
    private BankStatementService statementService;
    private PortalOrchestrationService portalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountService = mock(ClientAccountService.class);
        suspensionService = mock(AccountSuspensionService.class);
        statusService = mock(AccountStatusStatementService.class);
        statementService = mock(BankStatementService.class);
        portalService = mock(PortalOrchestrationService.class);
        AccountRepository accounts = mock(AccountRepository.class);
        when(accounts.findCustomerIdByAccountNumber("CH-MINE")).thenReturn(Optional.of(5L));
        when(accounts.findCustomerIdByAccountNumber("CH-THEIRS")).thenReturn(Optional.of(6L));
        CustomerAccessService access = new CustomerAccessService(accounts);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ClientAccountController(accountService, statementService, statusService, suspensionService, access),
                        new PortalController(portalService, access))
                .setControllerAdvice(new BankingExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .defaultRequest(get("/").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L))
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    private static String withdraw(String account) {
        return "{\"accountNumber\":\"" + account + "\",\"accountType\":\"CHECKING\",\"withdrawAmount\":10,\"street\":\"1 Main St\","
                + "\"city\":\"Austin\",\"state\":\"TX\",\"zip\":\"78701\"}";
    }

    private static String deposit(String account) {
        return "{\"accountNumber\":\"" + account + "\",\"amount\":10,\"accountType\":\"CHECKING\",\"depositType\":\"check\",\"street\":\"1 Main St\","
                + "\"addressLine1\":\"1 Main St\",\"city\":\"Austin\",\"state\":\"TX\",\"zip\":\"78701\"}";
    }

    @Test
    void anOwnAccountIsReachable() throws Exception {
        when(accountService.withdrawAndSaveToAccount(any())).thenReturn(Account.builder().checkingAccountNumber("CH-MINE")
                .accountType(AccountType.CHECKING).accountStatus(AccountStatus.ACTIVE).build());

        mockMvc.perform(post("/v1/api/accounts/withdraw").contentType(MediaType.APPLICATION_JSON).content(withdraw("CH-MINE")))
                .andExpect(status().isOk());
    }

    @Test
    void anotherCustomersAccountIs403OnEveryCustomerAccountEndpointAndNoServiceRuns() throws Exception {
        mockMvc.perform(post("/v1/api/accounts/withdraw").contentType(MediaType.APPLICATION_JSON).content(withdraw("CH-THEIRS")))
                .andExpect(status().isForbidden()).andExpect(content().string(DENIED));
        mockMvc.perform(post("/v1/api/accounts/deposit").contentType(MediaType.APPLICATION_JSON).content(deposit("CH-THEIRS")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/accounts/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"accountNumber\":\"CH-THEIRS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/accounts/CH-THEIRS/close")).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/accounts/CH-THEIRS/suspend").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/v1/api/accounts/CH-THEIRS/suspension").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/accounts/CH-THEIRS/reactivate")).andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/api/accounts/CH-THEIRS/statement").param("beginDate", "2026-09-01").param("endDate", "2026-09-30"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountService, suspensionService, statementService);
    }

    @Test
    void aBulkCloseContainingSomeoneElsesAccountIsRefusedAsAWhole() throws Exception {
        mockMvc.perform(post("/v1/api/accounts/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumbers\":[\"CH-MINE\",\"CH-THEIRS\"]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accountService);
    }

    @Test
    void theAccountListIsScopedToTheAuthenticatedCustomer() throws Exception {
        when(statusService.listAccountStatuses(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        // only the call into the service matters here (the standalone Jackson setup can't serialise a Spring Data Page)
        mockMvc.perform(get("/v1/api/accounts").param("accountNumber", "CH-THEIRS"));

        verify(statusService).listAccountStatuses(eq("CH-THEIRS"), any(), any(), any(), any(), any(), any(), eq(5L), any());
    }

    @Test
    void withoutAnAuthenticatedCustomerEveryHandlerFailsClosedWith401() throws Exception {
        MockMvc noAuth = MockMvcBuilders.standaloneSetup(
                        new ClientAccountController(accountService, statementService, statusService, suspensionService,
                                new CustomerAccessService(mock(AccountRepository.class))))
                .setControllerAdvice(new BankingExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        noAuth.perform(get("/v1/api/accounts")).andExpect(status().isUnauthorized());
        noAuth.perform(post("/v1/api/accounts/CH-MINE/close")).andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService, statusService);
    }

    @Test
    void thePortalOnlyReachesOwnAccountsAndHomeIsScopedToTheCustomer() throws Exception {
        mockMvc.perform(get("/bff/v1/portal/accounts/CH-THEIRS/overview")).andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/portal/accounts/withdraw").contentType(MediaType.APPLICATION_JSON).content(withdraw("CH-THEIRS")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/portal/accounts/deposit").contentType(MediaType.APPLICATION_JSON).content(deposit("CH-THEIRS")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/portal/accounts/CH-THEIRS/suspend").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/portal/accounts/CH-THEIRS/reactivate")).andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/portal/accounts/CH-THEIRS/close")).andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/portal/accounts/CH-THEIRS/statement").param("beginDate", "2026-09-01").param("endDate", "2026-09-30"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(portalService);

        mockMvc.perform(get("/bff/v1/portal/home").param("state", "TX"));
        verify(portalService).home("TX", 5L);
    }
}
