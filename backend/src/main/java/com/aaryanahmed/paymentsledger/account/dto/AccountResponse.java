package com.aaryanahmed.paymentsledger.account.dto;

import com.aaryanahmed.paymentsledger.account.Account;
import com.aaryanahmed.paymentsledger.account.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse (Long id,
                               String ownerName,
                               String currency,
                               BigDecimal balance,
                               Instant createdAt,
                               AccountType accountType
){
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getAccountType()
        );
    }
}
