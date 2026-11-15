package com.aaryanahmed.paymentsledger.account;

import com.aaryanahmed.paymentsledger.account.dto.AccountResponse;
import com.aaryanahmed.paymentsledger.account.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
public class AccountController {

    private final AccountService accountService;
    public AccountController(AccountService accountService)
    {
        this.accountService = accountService;
    }

    @GetMapping("/accounts/{id}")
    public AccountResponse getAccount(@PathVariable Long id){
        return accountService.getAccount(id);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse addAccount(@RequestBody @Valid CreateAccountRequest request){
        return accountService.createAccount(request);
    }

    @GetMapping("/accounts")
    public List<AccountResponse> getAccounts(){
        return  accountService.getAllAccounts();
    }


}
