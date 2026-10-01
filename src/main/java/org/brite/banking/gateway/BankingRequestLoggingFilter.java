package org.brite.banking.gateway;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.messages.BankingMessages;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import java.util.regex.Pattern;

/**
 * Logs every banking request - method, URI + query string, and the request body (the
 * "request object") - at INFO, tagged with the btid that {@link BusinessTransactionIdFilter}
 * already put in MDC. Sits after that filter and {@link BankingRateLimitFilter} (see
 * ordering in {@link BankingGatewayConfig}), so requests the rate limiter rejects never
 * reach it.
 * <p>
 * The body is only readable once a controller has consumed it, so the log line is written
 * <em>after</em> the chain runs ({@link #afterRequest}), from the cached copy that
 * {@link AbstractRequestLoggingFilter} makes when payload logging is on. As a result the
 * line is still emitted (with whatever body was read) when the controller rejects the
 * request. {@code beforeRequest} is deliberately a no-op: the body isn't cached yet, so a
 * "before" line would always log an empty payload.
 * <p>
 * Date of birth, phone number and login passwords are masked before logging - they're the fields in these payloads
 * that are more sensitive than the names/addresses the app already logs elsewhere. Headers are not
 * logged at all.
 */
@Slf4j
public class BankingRequestLoggingFilter extends AbstractRequestLoggingFilter {
    private static final Pattern DATE_OF_BIRTH = Pattern.compile("(\"dateOfBirth\"\\s*:\\s*\")[^\"]*(\")");
    private static final Pattern PHONE_NUMBER = Pattern.compile("(\"phoneNumber\"\\s*:\\s*\")[^\"]*(\")");
    /** A JSON string (with escapes) or a bare number/literal, so no form of the value survives. */
    private static final Pattern PASSWORD = Pattern.compile("(\"password\"\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\s]+)", Pattern.CASE_INSENSITIVE);

    public BankingRequestLoggingFilter(int maxPayloadLength) {
        setIncludeQueryString(true);
        setIncludePayload(true);
        setMaxPayloadLength(maxPayloadLength);
        setAfterMessagePrefix("");
        setAfterMessageSuffix("");
    }

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        // Body isn't cached yet - logged from afterRequest instead.
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        log.info(BankingMessages.LOG_REQUEST_RECEIVED, mask(message));
    }

    static String mask(String message) {
        String masked = DATE_OF_BIRTH.matcher(message).replaceAll("$1***$2");
        masked = PHONE_NUMBER.matcher(masked).replaceAll("$1***$2");
        return PASSWORD.matcher(masked).replaceAll("$1\"***\"");
    }
}
