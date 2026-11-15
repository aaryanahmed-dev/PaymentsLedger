package com.aaryanahmed.paymentsledger.account;

import com.aaryanahmed.paymentsledger.account.dto.AccountResponse;
import com.aaryanahmed.paymentsledger.account.dto.CreateAccountRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepo;

    public AccountService(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    public AccountResponse createAccount(CreateAccountRequest request) {
        Account account = new Account(request.ownerName(), request.currency());
        Account saved = accountRepo.save(account);
        return AccountResponse.from(saved);
    }

    public AccountResponse getAccount(Long id) {
        Account account = accountRepo.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return AccountResponse.from(account);
    }

    public List<AccountResponse> getAllAccounts() {
        List<Account> accounts = accountRepo.findAll();
        List<AccountResponse> accountResponses = new ArrayList<>();
        for (Account account : accounts) {
            accountResponses.add(AccountResponse.from(account));
        }
        return accountResponses;
    }
}