package com.paystream.controller;

import com.paystream.model.Account;
import com.paystream.model.LedgerEntry;
import com.paystream.repository.AccountRepository;
import com.paystream.service.LedgerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public AccountController(AccountRepository accountRepository, LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @GetMapping("/{accountNumber}")
    public Account getAccount(@PathVariable String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @GetMapping("/{accountNumber}/statement")
    public List<LedgerEntry> getStatement(@PathVariable String accountNumber) {
        return ledgerService.getAccountStatement(accountNumber);
    }
}
