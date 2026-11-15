package com.aaryanahmed.paymentsledger.ledger.dto;

import com.aaryanahmed.paymentsledger.ledger.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(Long id,
                                  Long transferId,
                                  Long accountId,
                                  BigDecimal amount,
                                  Instant createdAt
){
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransferId(),
                entry.getAccountId(),
                entry.getAmount(),
                entry.getCreatedAt()
        );
    }
}
