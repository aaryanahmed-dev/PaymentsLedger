package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.account.Account;
import com.aaryanahmed.paymentsledger.account.AccountNotFoundException;
import com.aaryanahmed.paymentsledger.account.AccountRepository;
import com.aaryanahmed.paymentsledger.account.AccountType;
import com.aaryanahmed.paymentsledger.ledger.LedgerEntry;
import com.aaryanahmed.paymentsledger.ledger.LedgerEntryRepository;
import com.aaryanahmed.paymentsledger.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final AccountRepository accountRepo;
    private final TransferRepository transferRepo;
    private final LedgerEntryRepository entryRepo;

    public TransferService(AccountRepository accountRepo,
                           TransferRepository transferRepo,
                           LedgerEntryRepository entryRepo) {
        this.accountRepo = accountRepo;
        this.transferRepo = transferRepo;
        this.entryRepo = entryRepo;
    }

    /**
     * Moves money between two accounts. Everything in here happens in ONE database
     * transaction: either the transfer, both ledger entries and both balance updates
     * are all saved, or (if any exception is thrown) none of them are.
     */
    @Transactional
    public TransferResponse transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        validateAmount(amount);
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        Account from = findAccount(fromAccountId);
        Account to = findAccount(toAccountId);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }

        // The accounts are "managed" entities, so Hibernate notices the changed balances
        // and writes the UPDATEs itself when the transaction commits (dirty checking).
        from.debit(amount);
        to.credit(amount);

        Transfer transfer = transferRepo.save(new Transfer(fromAccountId, toAccountId, amount, from.getCurrency()));

        // Double entry: one negative and one positive entry that cancel out.
        entryRepo.save(new LedgerEntry(transfer.getId(), fromAccountId, amount.negate()));
        entryRepo.save(new LedgerEntry(transfer.getId(), toAccountId, amount));

        return TransferResponse.from(transfer);
    }

    /** A deposit is a transfer from the EXTERNAL funding account of the same currency. */
    @Transactional
    public TransferResponse deposit(Long accountId, BigDecimal amount) {
        Account account = findAccount(accountId);
        Account funding = accountRepo.findFirstByAccountTypeAndCurrency(AccountType.EXTERNAL, account.getCurrency())
                .orElseThrow(() -> new UnsupportedCurrencyException(account.getCurrency()));
        return transfer(funding.getId(), accountId, amount);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long id) {
        Transfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
        return TransferResponse.from(transfer);
    }

    private Account findAccount(Long id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new InvalidTransferException("Amount must have at most 2 decimal places");
        }
    }
}
