package com.paystream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.exception.InsufficientBalanceException;
import com.paystream.model.Account;
import com.paystream.model.IdempotencyRecord;
import com.paystream.model.PaymentRequestDTO;
import com.paystream.model.PaymentResponseDTO;
import com.paystream.repository.AccountRepository;
import com.paystream.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private IdempotencyService idempotencyService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(accountRepository, transactionRepository, ledgerService, idempotencyService, objectMapper);
    }

    @Test
    @DisplayName("Should process payment successfully between two valid accounts")
    void shouldProcessPaymentSuccessfully() {
        PaymentRequestDTO request = PaymentRequestDTO.builder()
                .senderAccountNumber("ACC-1001")
                .receiverAccountNumber("ACC-1002")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        Account sender = Account.builder().id(1L).accountNumber("ACC-1001").holderName("Alice").balance(new BigDecimal("500.00")).currency("USD").version(0L).build();
        Account receiver = Account.builder().id(2L).accountNumber("ACC-1002").holderName("Bob").balance(new BigDecimal("100.00")).currency("USD").version(0L).build();

        when(idempotencyService.getRecord("idemp-key-1")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumber("ACC-1001")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber("ACC-1002")).thenReturn(Optional.of(receiver));

        PaymentResponseDTO response = paymentService.processPayment("idemp-key-1", request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("ACC-1001", response.getSenderAccountNumber());
        assertEquals("ACC-1002", response.getReceiverAccountNumber());

        assertEquals(0, new BigDecimal("400.00").compareTo(sender.getBalance()));
        assertEquals(0, new BigDecimal("200.00").compareTo(receiver.getBalance()));

        verify(ledgerService, times(1)).recordEntries(anyString(), eq(sender), eq(receiver), eq(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Should return cached response when idempotency key already exists")
    void shouldReturnCachedResponseWhenIdempotencyKeyExists() throws Exception {
        PaymentRequestDTO request = PaymentRequestDTO.builder()
                .senderAccountNumber("ACC-1001")
                .receiverAccountNumber("ACC-1002")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        PaymentResponseDTO cachedDTO = PaymentResponseDTO.builder()
                .transactionReference("TX-CACHED-123")
                .idempotencyKey("idemp-key-reuse")
                .senderAccountNumber("ACC-1001")
                .receiverAccountNumber("ACC-1002")
                .amount(new BigDecimal("100.00"))
                .status("COMPLETED")
                .message("Payment executed successfully")
                .build();

        String cachedJson = objectMapper.writeValueAsString(cachedDTO);
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey("idemp-key-reuse")
                .responseBody(cachedJson)
                .responseStatus(200)
                .build();

        when(idempotencyService.getRecord("idemp-key-reuse")).thenReturn(Optional.of(record));

        PaymentResponseDTO response = paymentService.processPayment("idemp-key-reuse", request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("TX-CACHED-123", response.getTransactionReference());

        // Verify ledger service and balance updates were NEVER executed (Zero double charges)
        verify(ledgerService, never()).recordEntries(any(), any(), any(), any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException when sender lacks funds")
    void shouldFailWhenInsufficientBalance() {
        PaymentRequestDTO request = PaymentRequestDTO.builder()
                .senderAccountNumber("ACC-1001")
                .receiverAccountNumber("ACC-1002")
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .build();

        Account sender = Account.builder().id(1L).accountNumber("ACC-1001").balance(new BigDecimal("10.00")).currency("USD").build();
        Account receiver = Account.builder().id(2L).accountNumber("ACC-1002").balance(new BigDecimal("100.00")).currency("USD").build();

        when(idempotencyService.getRecord("idemp-key-fail")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumber("ACC-1001")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber("ACC-1002")).thenReturn(Optional.of(receiver));

        assertThrows(InsufficientBalanceException.class, () -> paymentService.processPayment("idemp-key-fail", request));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when sender equals receiver")
    void shouldFailWhenSenderEqualsReceiver() {
        PaymentRequestDTO request = PaymentRequestDTO.builder()
                .senderAccountNumber("ACC-1001")
                .receiverAccountNumber("ACC-1001")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        when(idempotencyService.getRecord("idemp-same")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment("idemp-same", request));
    }
}
