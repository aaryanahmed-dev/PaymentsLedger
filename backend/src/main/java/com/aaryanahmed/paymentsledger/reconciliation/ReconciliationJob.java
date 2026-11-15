package com.aaryanahmed.paymentsledger.reconciliation;

import com.aaryanahmed.paymentsledger.reconciliation.dto.ReconciliationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the reconciliation regularly, so a broken ledger is noticed without anyone asking. */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${reconciliation.interval-ms}", initialDelayString = "${reconciliation.interval-ms}")
    public void run() {
        ReconciliationReport report = reconciliationService.reconcile();
        if (report.balanced()) {
            log.info("Reconciliation OK: {} accounts, {} entries, ledger total {}",
                    report.accountsChecked(), report.entriesChecked(), report.ledgerTotal());
        } else {
            // In a real system this is where an alert would be raised.
            log.error("Reconciliation FAILED: ledger total {}, mismatched accounts {}",
                    report.ledgerTotal(), report.mismatches());
        }
    }
}
