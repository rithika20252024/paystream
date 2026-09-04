package com.paystream.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.exception.InsufficientBalanceException;
import com.paystream.model.Account;
import com.paystream.model.IdempotencyRecord;
import com.paystream.model.PaymentRequestDTO;
import com.paystream.model.PaymentResponseDTO;
import com.paystream.model.PaymentTransaction;
import com.paystream.repository.AccountRepository;
import com.paystream.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service orchestrating payment processing, idempotency checks,
 * balance updates, and double-entry ledger logging.
 */
@Slf4j
@Service
public class PaymentService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          LedgerService ledgerService,
                          IdempotencyService idempotencyService,
                          ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponseDTO processPayment(String idempotencyKey, PaymentRequestDTO request) {
        // 1. Idempotency check — Return cached response if key exists
        Optional<IdempotencyRecord> recordOpt = idempotencyService.getRecord(idempotencyKey);
        if (recordOpt.isPresent()) {
            try {
                log.info("Idempotency hit for key: {}. Returning cached response.", idempotencyKey);
                return objectMapper.readValue(recordOpt.get().getResponseBody(), PaymentResponseDTO.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize cached idempotency response", e);
                throw new RuntimeException("Failed to read cached idempotent response");
            }
        }

        // 2. Validation
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (request.getSenderAccountNumber() == null || request.getReceiverAccountNumber() == null) {
            throw new IllegalArgumentException("Sender and receiver account numbers are required");
        }
        if (request.getSenderAccountNumber().equals(request.getReceiverAccountNumber())) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same");
        }

        Account sender = accountRepository.findByAccountNumber(request.getSenderAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Sender account not found: " + request.getSenderAccountNumber()));
        Account receiver = accountRepository.findByAccountNumber(request.getReceiverAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Receiver account not found: " + request.getReceiverAccountNumber()));

        // 3. Balance verification
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in sender account: " + sender.getAccountNumber());
        }

        // 4. Create PaymentTransaction record in PROCESSING state
        String txRef = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PaymentTransaction tx = PaymentTransaction.builder()
                .transactionReference(txRef)
                .idempotencyKey(idempotencyKey)
                .senderAccountNumber(sender.getAccountNumber())
                .receiverAccountNumber(receiver.getAccountNumber())
                .amount(amount)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .status("PROCESSING")
                .build();
        transactionRepository.save(tx);

        // 5. Execute Money Transfer (Atomic balance update)
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        // 6. Record Double-Entry Ledger Entries
        ledgerService.recordEntries(txRef, sender, receiver, amount);

        // 7. Update Transaction state to COMPLETED
        tx.setStatus("COMPLETED");
        transactionRepository.save(tx);

        // 8. Build PaymentResponseDTO
        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .transactionReference(txRef)
                .idempotencyKey(idempotencyKey)
                .senderAccountNumber(sender.getAccountNumber())
                .receiverAccountNumber(receiver.getAccountNumber())
                .amount(amount)
                .currency(tx.getCurrency())
                .status("COMPLETED")
                .message("Payment executed successfully")
                .timestamp(LocalDateTime.now().toString())
                .build();

        // 9. Save Idempotency Record for future duplicate requests
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            idempotencyService.saveRecord(idempotencyKey, String.valueOf(request.hashCode()), responseJson, 200);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response for idempotency record", e);
        }

        return response;
    }
}
