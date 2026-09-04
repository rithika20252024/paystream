package com.paystream.integration;

import com.paystream.model.Account;
import com.paystream.model.PaymentRequestDTO;
import com.paystream.model.PaymentResponseDTO;
import com.paystream.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Payment System Integration Tests")
class PaymentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setup() {
        // Ensure test accounts exist and reset balances
        Account acc1 = accountRepository.findByAccountNumber("ACC-1001")
                .orElseGet(() -> accountRepository.save(Account.builder().accountNumber("ACC-1001").holderName("Alice Vance").balance(new BigDecimal("1000.00")).currency("USD").version(0L).build()));
        acc1.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(acc1);

        Account acc2 = accountRepository.findByAccountNumber("ACC-1002")
                .orElseGet(() -> accountRepository.save(Account.builder().accountNumber("ACC-1002").holderName("Bob Smith").balance(new BigDecimal("500.00")).currency("USD").version(0L).build()));
        acc2.setBalance(new BigDecimal("500.00"));
        accountRepository.save(acc2);
    }

    @Test
    @DisplayName("Should process single payment end-to-end successfully")
    void shouldProcessSinglePaymentEndToEnd() {
        PaymentRequestDTO request = PaymentRequestDTO.builder()
                .senderAccountNumber("ACC-1001")
                .receiverAccountNumber("ACC-1002")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        HttpEntity<PaymentRequestDTO> entity = new HttpEntity<>(request, headers);

        ResponseEntity<PaymentResponseDTO> response = restTemplate.exchange("/api/v1/payments", HttpMethod.POST, entity, PaymentResponseDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("COMPLETED", response.getBody().getStatus());

        Account sender = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
        assertEquals(0, new BigDecimal("900.00").compareTo(sender.getBalance()));
    }

    @Test
    @DisplayName("Should prevent double-charging when request is retried with duplicate Idempotency-Key")
    void shouldPreventDoubleChargingOnDuplicateIdempotencyKey() {
        PaymentRequestDTO request = PaymentRequestDTO.builder()
                .senderAccountNumber("ACC-1001")
                .receiverAccountNumber("ACC-1002")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        HttpEntity<PaymentRequestDTO> entity = new HttpEntity<>(request, headers);

        // First Payment Request
        ResponseEntity<PaymentResponseDTO> response1 = restTemplate.exchange("/api/v1/payments", HttpMethod.POST, entity, PaymentResponseDTO.class);
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertEquals("COMPLETED", response1.getBody().getStatus());

        // Retry Request (Duplicate Idempotency Key)
        ResponseEntity<PaymentResponseDTO> response2 = restTemplate.exchange("/api/v1/payments", HttpMethod.POST, entity, PaymentResponseDTO.class);
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        assertEquals(response1.getBody().getTransactionReference(), response2.getBody().getTransactionReference());

        // Verify balance was deducted EXACTLY ONCE
        Account sender = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
        assertEquals(0, new BigDecimal("900.00").compareTo(sender.getBalance()));
    }

    @Test
    @DisplayName("Should handle 10 concurrent payments safely with zero money leakage")
    void shouldHandleConcurrentPaymentsSafely() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulTransfers = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Synchronized start
                    PaymentRequestDTO request = PaymentRequestDTO.builder()
                            .senderAccountNumber("ACC-1001")
                            .receiverAccountNumber("ACC-1002")
                            .amount(new BigDecimal("10.00"))
                            .currency("USD")
                            .build();

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());
                    HttpEntity<PaymentRequestDTO> entity = new HttpEntity<>(request, headers);

                    ResponseEntity<PaymentResponseDTO> response = restTemplate.exchange("/api/v1/payments", HttpMethod.POST, entity, PaymentResponseDTO.class);
                    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && "COMPLETED".equals(response.getBody().getStatus())) {
                        successfulTransfers.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        completionLatch.await(); // Wait for all threads to finish

        Account sender = accountRepository.findByAccountNumber("ACC-1001").orElseThrow();
        Account receiver = accountRepository.findByAccountNumber("ACC-1002").orElseThrow();

        // Total system balance (initial 1000 + 500 = 1500) must be 100% preserved
        BigDecimal totalBalance = sender.getBalance().add(receiver.getBalance());
        assertEquals(0, new BigDecimal("1500.00").compareTo(totalBalance), "Total system money must remain exactly $1500.00");

        executorService.shutdown();
    }
}
