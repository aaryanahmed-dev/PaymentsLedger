package com.aaryanahmed.paymentsledger.account;

import com.aaryanahmed.paymentsledger.account.dto.AccountResponse;
import com.aaryanahmed.paymentsledger.account.dto.CreateAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;
    public AccountController(AccountService accountService)
    {
        this.accountService = accountService;
    }

    @Operation(summary = "Get one account with its current balance")
    @GetMapping("/accounts/{id}")
    public AccountResponse getAccount(@PathVariable Long id){
        return accountService.getAccount(id);
    }

    @Operation(summary = "Create a customer account with a zero balance")
    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse addAccount(@RequestBody @Valid CreateAccountRequest request){
        return accountService.createAccount(request);
    }

    @Operation(summary = "List all accounts")
    @GetMapping("/accounts")
    public List<AccountResponse> getAccounts(){
        return  accountService.getAllAccounts();
    }


}
