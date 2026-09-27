package com.aaryanahmed.paymentsledger.account.dto;

import com.aaryanahmed.paymentsledger.account.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse (Long id,
                               String ownerName,
                               String currency,
                               BigDecimal balance,
                               Instant createdAt
){
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt()
        );
    }
}
