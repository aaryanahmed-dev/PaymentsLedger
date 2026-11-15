package com.aaryanahmed.paymentsledger.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account,Long> {

    // Spring Data derives the query from the method name:
    // SELECT ... WHERE account_type = ? AND currency = ? LIMIT 1
    Optional<Account> findFirstByAccountTypeAndCurrency(AccountType accountType, String currency);
}
