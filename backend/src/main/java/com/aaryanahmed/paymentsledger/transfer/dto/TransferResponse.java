package com.aaryanahmed.paymentsledger.transfer.dto;

import com.aaryanahmed.paymentsledger.transfer.Transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(Long id,
                               Long fromAccountId,
                               Long toAccountId,
                               BigDecimal amount,
                               String currency,
                               Instant createdAt
){
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getCreatedAt()
        );
    }
}
