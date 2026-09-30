package org.bee.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.bee.banking.bff.controller.PortalController;
import org.bee.banking.bff.dto.AccountOverviewResponse;
import org.bee.banking.bff.service.PortalOrchestrationService;
import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountType;
import org.bee.banking.exception.AccountNotFoundException;
import org.bee.banking.exception.AccountSuspendedException;
import org.bee.banking.exception.BankingExceptionHandler;
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
class PortalControllerTest {
    private PortalOrchestrationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PortalOrchestrationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PortalController(service))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void overviewBindsDaysAndReturnsJson() throws Exception {
        when(service.overview("CH-0000088291", 7)).thenReturn(new AccountOverviewResponse("CH-0000088291", AccountType.CHECKING,
                AccountStatus.ACTIVE, new BigDecimal("500.00"), false, null, LocalDate.of(2026, 1, 5), null, "Ada", "Lovelace", 7, List.of()));

        mockMvc.perform(get("/bff/v1/portal/accounts/CH-0000088291/overview").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("CH-0000088291"))
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.createdDate").value("2026-01-05"))
                .andExpect(jsonPath("$.suspended").value(false))
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
    void openAccountRejectsInvalidBodyBeforeCallingService() throws Exception {
        mockMvc.perform(post("/bff/v1/portal/accounts/open").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).openAccount(any());
    }

    private AccountOverviewResponse suspendedOverview() {
        return new AccountOverviewResponse("CH-0000010001", AccountType.CHECKING, AccountStatus.SUSPENDED,
                new BigDecimal("75.00"), true, java.time.LocalDateTime.of(2026, 12, 31, 17, 0),
                LocalDate.of(2026, 1, 5), null, "Ada", "Lovelace", 30, List.of());
    }

    @Test
    void suspendReturnsTheRefreshedOverview() throws Exception {
        when(service.suspend(eq("CH-0000010001"), any())).thenReturn(suspendedOverview());

        mockMvc.perform(post("/bff/v1/portal/accounts/CH-0000010001/suspend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Fraud review\",\"endDateTime\":\"2026-12-31T17:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.suspendedUntil").value("2026-12-31T17:00:00"));
    }

    @Test
    void suspendWithBlankNotesIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/bff/v1/portal/accounts/CH-0000010001/suspend").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).suspend(any(), any());
    }

    @Test
    void patchSuspensionReturnsTheRefreshedOverview() throws Exception {
        when(service.updateSuspension(eq("CH-0000010001"), any())).thenReturn(suspendedOverview());

        mockMvc.perform(patch("/bff/v1/portal/accounts/CH-0000010001/suspension").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"extended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspended").value(true));
    }

    @Test
    void reactivateReturnsTheRefreshedOverview() throws Exception {
        when(service.reactivate("CH-0000010001")).thenReturn(new AccountOverviewResponse("CH-0000010001", AccountType.CHECKING,
                AccountStatus.ACTIVE, new BigDecimal("75.00"), false, null, LocalDate.of(2026, 1, 5), null, "Ada", "Lovelace", 30, List.of()));

        mockMvc.perform(post("/bff/v1/portal/accounts/CH-0000010001/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.suspended").value(false));
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
