package com.aaryanahmed.paymentsledger.account;

import com.aaryanahmed.paymentsledger.account.dto.AccountResponse;
import com.aaryanahmed.paymentsledger.account.dto.CreateAccountRequest;
import org.springframework.stereotype.Service;

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
}