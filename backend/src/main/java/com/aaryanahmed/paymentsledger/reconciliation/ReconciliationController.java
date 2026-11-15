package com.aaryanahmed.paymentsledger.reconciliation;

import com.aaryanahmed.paymentsledger.reconciliation.dto.ReconciliationReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    // Always 200: the request worked. Whether the ledger is healthy is the "balanced" field.
    @Operation(summary = "Check that all ledger entries sum to zero and every balance matches its entries")
    @GetMapping("/admin/reconciliation")
    public ReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }
}
