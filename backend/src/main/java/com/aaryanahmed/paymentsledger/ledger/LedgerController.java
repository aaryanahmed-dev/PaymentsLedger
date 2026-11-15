package com.aaryanahmed.paymentsledger.ledger;

import com.aaryanahmed.paymentsledger.common.PageResponse;
import com.aaryanahmed.paymentsledger.ledger.dto.LedgerEntryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts/{id}/entries")
    public PageResponse<LedgerEntryResponse> getEntries(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ledgerService.getEntries(id, page, size);
    }
}
