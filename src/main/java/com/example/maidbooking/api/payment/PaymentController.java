package com.example.maidbooking.api.payment;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.payment.IdempotentResult;
import com.example.maidbooking.payment.PaymentReceipt;
import com.example.maidbooking.payment.PaymentService;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** A replay of an earlier request with the same key returns the same body, marked {@code Idempotent-Replayed}. */
    @PostMapping("/api/v1/jobs/{jobId}/payments")
    public ResponseEntity<PaymentResponse> pay(@PathVariable String jobId, @Valid @RequestBody PaymentBody body,
                                               @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        IdempotentResult<PaymentReceipt> result = paymentService.pay(jobId, body.details(), key);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(PaymentResponse.from(result.value()));
    }
}
