package org.brite.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Plain unit test (no Spring context) for the banking gateway's business-transaction-id
 * filter: it must put a unique btid into MDC before the request is chained through,
 * echo it as a response header, and always clear the MDC entry afterward - even when
 * the downstream chain throws - so it never leaks onto Tomcat's reused worker threads.
 */
@ExtendWith(MockitoExtension.class)
class BusinessTransactionIdFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final BusinessTransactionIdFilter filter = new BusinessTransactionIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void doFilter_setsBtidInMdcWhileChaining_andEchoesItAsResponseHeader() throws Exception {
        String[] btidDuringChain = new String[1];
        doAnswer(invocation -> {
            btidDuringChain[0] = MDC.get(BusinessTransactionIdFilter.MDC_KEY);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(btidDuringChain[0]).isNotBlank();
        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(BusinessTransactionIdFilter.RESPONSE_HEADER), headerValue.capture());
        assertThat(headerValue.getValue()).isEqualTo(btidDuringChain[0]);
    }

    @Test
    void doFilter_clearsMdcAfterChainCompletes() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(MDC.get(BusinessTransactionIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilter_clearsMdcEvenWhenChainThrows() throws Exception {
        RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(filterChain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isSameAs(failure);

        assertThat(MDC.get(BusinessTransactionIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilter_generatesADifferentBtidPerRequest() throws Exception {
        ArgumentCaptor<String> headerValues = ArgumentCaptor.forClass(String.class);

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(response, org.mockito.Mockito.times(2))
                .setHeader(org.mockito.ArgumentMatchers.eq(BusinessTransactionIdFilter.RESPONSE_HEADER), headerValues.capture());
        assertThat(headerValues.getAllValues()).doesNotHaveDuplicates();
    }
}
