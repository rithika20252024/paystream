package com.paystream.controller;

import com.paystream.model.PaymentRequestDTO;
import com.paystream.model.PaymentResponseDTO;
import com.paystream.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponseDTO> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequestDTO request) {

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Header 'Idempotency-Key' is required");
        }

        PaymentResponseDTO response = paymentService.processPayment(idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}
