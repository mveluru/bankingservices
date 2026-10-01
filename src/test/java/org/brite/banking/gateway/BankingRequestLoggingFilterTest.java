package org.brite.banking.gateway;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context): drives the filter with a mock servlet request whose
 * "controller" consumes the body, and reads what was logged via a Logback ListAppender.
 */
class BankingRequestLoggingFilterTest {

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(BankingRequestLoggingFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
    }

    private String runPost(BankingRequestLoggingFilter filter, String uri, String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        // Stand-in controller: reads the body, which is what fills the filter's cached copy.
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }

    @Test
    void logsMethodUriAndRequestBody() throws Exception {
        String message = runPost(new BankingRequestLoggingFilter(2000),
                "/v1/api/accounts/deposit", "{\"accountNumber\":\"SV-0000044102\",\"amount\":250.00}");

        assertThat(message).startsWith("Request POST /v1/api/accounts/deposit")
                .contains("payload={\"accountNumber\":\"SV-0000044102\",\"amount\":250.00}");
    }

    @Test
    void masksDateOfBirth() throws Exception {
        String message = runPost(new BankingRequestLoggingFilter(2000),
                "/v1/api/accounts/newaccount", "{\"firstName\":\"Jane\",\"dateOfBirth\": \"03/15/1990\",\"zip\":\"78717\"}");

        assertThat(message).contains("\"firstName\":\"Jane\"", "\"zip\":\"78717\"")
                .contains("\"dateOfBirth\": \"***\"")
                .doesNotContain("03/15/1990");
    }

    @Test
    void masksPhoneNumber() throws Exception {
        String message = runPost(new BankingRequestLoggingFilter(2000),
                "/v1/api/accounts/newaccount", "{\"firstName\":\"Jane\",\"phoneNumber\": \"512-555-0101\",\"dateOfBirth\":\"03/15/1990\"}");

        assertThat(message).contains("\"firstName\":\"Jane\"", "\"phoneNumber\": \"***\"", "\"dateOfBirth\":\"***\"")
                .doesNotContain("512-555-0101", "03/15/1990");
    }

    @Test
    void truncatesPayloadToMaxLength() throws Exception {
        String message = runPost(new BankingRequestLoggingFilter(10), "/v1/api/accounts/lookup", "0123456789ABCDEFGHIJ");

        assertThat(message).contains("0123456789").doesNotContain("ABCDEFGHIJ");
    }

    @Test
    void logsQueryStringForGetWithNoBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/api/accounts");
        request.setQueryString("status=ACTIVE&months=6");

        new BankingRequestLoggingFilter(2000).doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .startsWith("Request GET /v1/api/accounts?status=ACTIVE&months=6");
    }

    @Test
    void masksPasswordsInEveryJsonForm() {
        assertThat(BankingRequestLoggingFilter.mask("{\"username\":\"lucas.meyer\",\"password\":\"20260010\"}"))
                .contains("\"username\":\"lucas.meyer\"", "\"password\":\"***\"")
                .doesNotContain("20260010");
        assertThat(BankingRequestLoggingFilter.mask("{\"password\": 20260010, \"username\":\"x\"}"))
                .contains("\"password\": \"***\"", "\"username\":\"x\"")
                .doesNotContain("20260010");
        assertThat(BankingRequestLoggingFilter.mask("{\"Password\" : \"ab\\\"cd1234\"}"))
                .doesNotContain("cd1234", "ab");
    }
}
