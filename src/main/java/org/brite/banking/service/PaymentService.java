package org.brite.banking.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.component.BankClient;
import org.brite.banking.messages.BankingMessages;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {
      private final BankClient bankClient;

        public PaymentService(BankClient bankClient) {
            this.bankClient = bankClient;
        }

        // Retry wraps CircuitBreaker (Retry is the outer aspect), so each retry
        // attempt still respects the breaker's state. Only Retry declares a
        // fallback: if CircuitBreaker had its own, it would swallow the failure
        // and return normally before Retry ever saw an exception to retry on.
        @Retry(
                name = "bankService",
                fallbackMethod = "paymentFallback"
        )
        @CircuitBreaker(name = "bankService")
        public String processPayment() {
            log.debug(BankingMessages.LOG_PAYMENT_PROCESSING);
            return bankClient.processPayment();
        }

        public String paymentFallback(Throwable ex) {
            log.warn(BankingMessages.LOG_PAYMENT_FALLBACK_TRIGGERED, ex.getMessage());
            return "Payment service temporarily unavailable";
        }

}
