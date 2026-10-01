package org.brite.banking.component;

import org.brite.banking.exception.BankServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class BankClient {

    // Simulates a flaky downstream bank service so the circuit breaker/retry
    // configuration on PaymentService has real transient failures to react to.
    private static final double SIMULATED_FAILURE_RATE = 0.4;

    public String processPayment(){
        if (ThreadLocalRandom.current().nextDouble() < SIMULATED_FAILURE_RATE) {
            throw new BankServiceUnavailableException("Simulated bank service timeout");
        }
        return "Successfully Processed";
    }
}
