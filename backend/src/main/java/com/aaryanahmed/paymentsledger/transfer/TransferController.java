package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.idempotency.IdempotencyService;
import com.aaryanahmed.paymentsledger.transfer.dto.CreateTransferRequest;
import com.aaryanahmed.paymentsledger.transfer.dto.DepositRequest;
import com.aaryanahmed.paymentsledger.transfer.dto.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Transfers")
public class TransferController {

    private static final String IDEMPOTENCY_KEY_HELP = "A unique value chosen by the client for each new request, e.g. a UUID";
    private static final String IDEMPOTENCY_KEY_EXAMPLE = "3f1c9a52-7d7e-4b0a-9a57-2f6d1c0e8b11";

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferService transferService, IdempotencyService idempotencyService) {
        this.transferService = transferService;
        this.idempotencyService = idempotencyService;
    }

    @Operation(summary = "Move money between two accounts of the same currency",
            description = "Repeating a request with the same Idempotency-Key returns the original response "
                    + "without moving money again. The same key with a different body is a 409.")
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse createTransfer(@Parameter(description = IDEMPOTENCY_KEY_HELP, example = IDEMPOTENCY_KEY_EXAMPLE)
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           @RequestBody @Valid CreateTransferRequest request) {
        // What makes two requests "the same". 10.5 and 10.50 are the same amount.
        String sameRequest = "transfer:" + request.fromAccountId() + ":" + request.toAccountId()
                + ":" + request.amount().stripTrailingZeros().toPlainString();
        return idempotencyService.execute(idempotencyKey, sameRequest, TransferResponse.class,
                () -> transferService.transfer(request.fromAccountId(), request.toAccountId(), request.amount()));
    }

    @Operation(summary = "Reverse a transfer by creating the opposite transfer",
            description = "A transfer can be reversed once. Fails with 422 if the receiver no longer has the money.")
    @PostMapping("/transfers/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse reverseTransfer(@PathVariable Long id) {
        return transferService.reverse(id);
    }

    @Operation(summary = "Get one transfer")
    @GetMapping("/transfers/{id}")
    public TransferResponse getTransfer(@PathVariable Long id) {
        return transferService.getTransfer(id);
    }

    @Operation(summary = "Deposit money into an account",
            description = "Recorded as a transfer from the external funding account of the same currency (EUR, USD or GBP).")
    @PostMapping("/accounts/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse deposit(@PathVariable Long id,
                                    @Parameter(description = IDEMPOTENCY_KEY_HELP, example = IDEMPOTENCY_KEY_EXAMPLE)
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    @RequestBody @Valid DepositRequest request) {
        String sameRequest = "deposit:" + id + ":" + request.amount().stripTrailingZeros().toPlainString();
        return idempotencyService.execute(idempotencyKey, sameRequest, TransferResponse.class,
                () -> transferService.deposit(id, request.amount()));
    }
}
