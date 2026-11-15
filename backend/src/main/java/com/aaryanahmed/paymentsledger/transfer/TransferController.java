package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.idempotency.IdempotencyService;
import com.aaryanahmed.paymentsledger.transfer.dto.CreateTransferRequest;
import com.aaryanahmed.paymentsledger.transfer.dto.DepositRequest;
import com.aaryanahmed.paymentsledger.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferService transferService, IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse createTransfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                           @RequestBody @Valid CreateTransferRequest request) {
        // What makes two requests "the same". 10.5 and 10.50 are the same amount.
        String sameRequest = "transfer:" + request.fromAccountId() + ":" + request.toAccountId()
                + ":" + request.amount().stripTrailingZeros().toPlainString();
        return idempotencyService.execute(idempotencyKey, sameRequest, TransferResponse.class,
                () -> transferService.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
    }

    @PostMapping("/transfers/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse reverseTransfer(@PathVariable Long id) {
        return transferService.reverse(id);
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse getTransfer(@PathVariable Long id) {
        return transferService.getTransfer(id);
    }

    @PostMapping("/accounts/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse deposit(@PathVariable Long id,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @RequestBody @Valid DepositRequest request) {
        String sameRequest = "deposit:" + id + ":" + request.amount().stripTrailingZeros().toPlainString();
        return idempotencyService.execute(idempotencyKey, sameRequest, TransferResponse.class,
                () -> transferService.deposit(id, request.amount()));
    }
}
