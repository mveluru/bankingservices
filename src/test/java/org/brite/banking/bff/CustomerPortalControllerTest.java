package org.brite.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.CustomerPortalController;
import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.service.PortalOrchestrationService;
import org.brite.banking.gateway.CustomerAuthenticationFilter;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.service.CustomerAccessService;
import java.util.Optional;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.BankStatement;
import org.brite.banking.exception.AccountClosedException;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.AccountSuspendedException;
import org.brite.banking.exception.BankingExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc (no Spring context, so no MySQL): param binding, status codes, JSON shape, error mapping. */
class CustomerPortalControllerTest {
    private PortalOrchestrationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PortalOrchestrationService.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        when(accountRepository.findCustomerIdByAccountNumber(any())).thenReturn(Optional.of(5L));   // customer 5 owns every account here
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerPortalController(service, new CustomerAccessService(accountRepository)))
                .defaultRequest(get("/").requestAttr(CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE, 5L))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void overviewBindsDaysAndReturnsJson() throws Exception {
        when(service.overview("CH-0000088291", 7)).thenReturn(new AccountOverviewResponse("CH-0000088291", AccountType.CHECKING,
                AccountStatus.ACTIVE, new BigDecimal("500.00"), false, null, LocalDate.of(2026, 1, 5), null, "Ada", "Lovelace", "***-***-0101", null, 7, List.of()));

        mockMvc.perform(get("/bff/v1/portal/accounts/CH-0000088291/overview").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("CH-0000088291"))
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.createdDate").value("2026-01-05"))
                .andExpect(jsonPath("$.suspended").value(false))
                .andExpect(jsonPath("$.maskedPhoneNumber").value("***-***-0101"))
                .andExpect(jsonPath("$.activityDays").value(7));
    }

    @Test
    void unknownAccountIsPlainText404ViaBankingExceptionHandler() throws Exception {
        when(service.overview("CH-0000000000", null)).thenThrow(new AccountNotFoundException("Account not found: CH-0000000000"));

        mockMvc.perform(get("/bff/v1/portal/accounts/CH-0000000000/overview"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Account not found: CH-0000000000"));
    }

    @Test
    void invalidDaysIsPlainText400() throws Exception {
        when(service.overview("CH-0000088291", 500)).thenThrow(new IllegalArgumentException("days must be between 1 and 90"));

        mockMvc.perform(get("/bff/v1/portal/accounts/CH-0000088291/overview").param("days", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("days must be between 1 and 90"));
    }

    @Test
    void closeReturnsTheRefreshedOverview() throws Exception {
        when(service.close("CH-0000010001")).thenReturn(new AccountOverviewResponse("CH-0000010001", AccountType.CHECKING,
                AccountStatus.CLOSED, BigDecimal.ZERO, false, null, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 9, 30), "Ada", "Lovelace", "***-***-0101", null, 30, List.of()));

        mockMvc.perform(post("/bff/v1/portal/accounts/CH-0000010001/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("CLOSED"))
                .andExpect(jsonPath("$.closedDate").value("2026-09-30"));
    }

    @Test
    void closingAnAlreadyClosedAccountIsPlainText400() throws Exception {
        when(service.close("CH-0000030001")).thenThrow(new AccountClosedException("Account CH-0000030001 is already closed"));

        mockMvc.perform(post("/bff/v1/portal/accounts/CH-0000030001/close"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account CH-0000030001 is already closed"));
    }

    @Test
    void statementBindsDatesAndReturnsTheStatement() throws Exception {
        LocalDate begin = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 9, 24);
        when(service.statement("CH-0000088291", begin, end)).thenReturn(BankStatement.builder()
                .accountNumber("CH-0000088291").beginDate(begin).endDate(end).transactions(List.of()).build());

        mockMvc.perform(post("/bff/v1/portal/accounts/CH-0000088291/statement")
                        .param("beginDate", "2026-08-01").param("endDate", "2026-09-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("CH-0000088291"))
                .andExpect(jsonPath("$.beginDate").value("2026-08-01"))
                .andExpect(jsonPath("$.endDate").value("2026-09-24"));
    }

    @Test
    void statementIsPostOnlyBecauseItSendsNotifications() throws Exception {
        mockMvc.perform(get("/bff/v1/portal/accounts/CH-0000088291/statement")
                        .param("beginDate", "2026-08-01").param("endDate", "2026-09-24"))
                .andExpect(status().isMethodNotAllowed());
        verify(service, never()).statement(any(), any(), any());
    }

    @Test
    void suspendedAccountTransactionsArePlainText400() throws Exception {
        String message = "Account CH-0000050001 is suspended and cannot be used for transactions until it is reactivated";
        when(service.deposit(any())).thenThrow(new AccountSuspendedException(message));
        when(service.withdraw(any())).thenThrow(new AccountSuspendedException(message));
        String address = "\"street\":\"1\",\"addressLine1\":\"1 Main St\",\"city\":\"Austin\",\"state\":\"TX\",\"zip\":\"78701\"";

        mockMvc.perform(post("/bff/v1/portal/accounts/deposit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"CH-0000050001\",\"accountType\":\"CHECKING\",\"amount\":10,\"depositType\":\"check\"," + address + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(message));
        mockMvc.perform(post("/bff/v1/portal/accounts/withdraw").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"CH-0000050001\",\"accountType\":\"CHECKING\",\"withdrawAmount\":10," + address + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(message));
    }
}
