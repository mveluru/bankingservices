package org.brite.banking.contoller;

import lombok.RequiredArgsConstructor;
import org.brite.banking.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    /**
     * Demonstrates a resilience4j circuit breaker around an external bank call.
     * POST /v1/payment/process
     */
    @PostMapping("/process")
    public String processPayment() {
        return paymentService.processPayment();
    }
}
