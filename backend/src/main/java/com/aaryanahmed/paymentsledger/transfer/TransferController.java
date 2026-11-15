package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.transfer.dto.CreateTransferRequest;
import com.aaryanahmed.paymentsledger.transfer.dto.DepositRequest;
import com.aaryanahmed.paymentsledger.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse createTransfer(@RequestBody @Valid CreateTransferRequest request) {
        return transferService.transfer(request.fromAccountId(), request.toAccountId(), request.amount());
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse getTransfer(@PathVariable Long id) {
        return transferService.getTransfer(id);
    }

    @PostMapping("/accounts/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse deposit(@PathVariable Long id, @RequestBody @Valid DepositRequest request) {
        return transferService.deposit(id, request.amount());
    }
}
