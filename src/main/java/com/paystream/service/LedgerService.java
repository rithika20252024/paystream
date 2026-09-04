package com.paystream.service;

import com.paystream.model.Account;
import com.paystream.model.LedgerEntry;
import com.paystream.repository.LedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public void recordEntries(String txRef, Account sender, Account receiver, BigDecimal amount) {
        // Create DEBIT entry for sender
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transactionReference(txRef)
                .accountNumber(sender.getAccountNumber())
                .entryType("DEBIT")
                .amount(amount)
                .balanceAfter(sender.getBalance())
                .build();

        // Create CREDIT entry for receiver
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transactionReference(txRef)
                .accountNumber(receiver.getAccountNumber())
                .entryType("CREDIT")
                .amount(amount)
                .balanceAfter(receiver.getBalance())
                .build();

        ledgerRepository.save(debitEntry);
        ledgerRepository.save(creditEntry);
    }

    public List<LedgerEntry> getAccountStatement(String accountNumber) {
        return ledgerRepository.findByAccountNumberOrderByCreatedAtDesc(accountNumber);
    }
}
