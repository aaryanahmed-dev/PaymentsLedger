package com.aaryanahmed.paymentsledger.reconciliation;

import com.aaryanahmed.paymentsledger.reconciliation.dto.ReconciliationReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    // Always 200: the request worked. Whether the ledger is healthy is the "balanced" field.
    @GetMapping("/admin/reconciliation")
    public ReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }
}
