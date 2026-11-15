package com.aaryanahmed.paymentsledger.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account,Long> {

    /**
     * Loads the account with SELECT ... FOR UPDATE. The row stays locked until the
     * surrounding transaction ends, so any other transaction asking for the same row
     * waits, and then reads the balance this transaction committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    // The two lookups below return single columns instead of Account entities on purpose.
    // If they loaded the entity, Hibernate would keep that (unlocked, possibly outdated) copy
    // and reuse it when findByIdForUpdate runs later in the same transaction.

    @Query("select a.currency from Account a where a.id = :id")
    Optional<String> findCurrencyById(@Param("id") Long id);

    @Query("select min(a.id) from Account a where a.accountType = :accountType and a.currency = :currency")
    Optional<Long> findIdByAccountTypeAndCurrency(@Param("accountType") AccountType accountType,
                                                  @Param("currency") String currency);
}
