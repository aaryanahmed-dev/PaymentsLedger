package com.aaryanahmed.paymentsledger.reconciliation.dto;

import java.math.BigDecimal;

/** An account whose stored balance is not the sum of its ledger entries. */
public record AccountMismatch(Long accountId,
                              BigDecimal balance,
                              BigDecimal entriesSum,
                              BigDecimal difference)
{ }
