package com.aaryanahmed.paymentsledger.reconciliation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReconciliationReport(Instant checkedAt,
                                   boolean balanced,
                                   BigDecimal ledgerTotal,
                                   long accountsChecked,
                                   long entriesChecked,
                                   List<AccountMismatch> mismatches)
{ }
