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
        return TransferResponse.from(execute(fromAccountId, toAccountId, amount, null));
    }

    /**
     * Undoes a transfer by creating the opposite one, linked to the original.
     * Nothing is deleted or edited: the original and its entries stay in the ledger.
     */
    @Transactional
    public TransferResponse reverse(Long transferId) {
        // Locking the original makes two simultaneous reversals of it take turns.
        Transfer original = transferRepo.findByIdForUpdate(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        if (original.isReversal()) {
            throw new TransferNotReversibleException("Transfer " + transferId + " is itself a reversal");
        }
        if (transferRepo.existsByReversalOf(transferId)) {
            throw new TransferNotReversibleException("Transfer " + transferId + " has already been reversed");
        }
        // Same amount, sender and receiver swapped. The original receiver must still have
        // the money: debit() applies the usual overdraft rule.
        return TransferResponse.from(execute(
                original.getToAccountId(), original.getFromAccountId(), original.getAmount(), transferId));
    }

    private Transfer execute(Long fromAccountId, Long toAccountId, BigDecimal amount, Long reversalOf) {
        // Lock both account rows before reading their balances, always lowest id first.
        // If A->B and B->A run at the same time, both try to lock the same row first,
        // so one simply waits for the other instead of each holding the row the other needs.
        Account lower = lockAccount(Math.min(fromAccountId, toAccountId));
        Account higher = lockAccount(Math.max(fromAccountId, toAccountId));
        Account from = fromAccountId < toAccountId ? lower : higher;
        Account to = fromAccountId < toAccountId ? higher : lower;
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }

        // The accounts are "managed" entities, so Hibernate notices the changed balances
        // and writes the UPDATEs itself when the transaction commits (dirty checking).
        from.debit(amount);
        to.credit(amount);

        Transfer transfer = transferRepo.save(
                new Transfer(fromAccountId, toAccountId, amount, from.getCurrency(), reversalOf));

        // Double entry: one negative and one positive entry that cancel out.
        entryRepo.save(new LedgerEntry(transfer.getId(), fromAccountId, amount.negate()));
        entryRepo.save(new LedgerEntry(transfer.getId(), toAccountId, amount));

        return transfer;
    }

    /** A deposit is a transfer from the EXTERNAL funding account of the same currency. */
    @Transactional
    public TransferResponse deposit(Long accountId, BigDecimal amount) {
        String currency = accountRepo.findCurrencyById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        Long fundingAccountId = accountRepo.findIdByAccountTypeAndCurrency(AccountType.EXTERNAL, currency)
                .orElseThrow(() -> new UnsupportedCurrencyException(currency));
        return transfer(fundingAccountId, accountId, amount);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long id) {
        Transfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
        return TransferResponse.from(transfer);
    }

    private Account lockAccount(Long id) {
        return accountRepo.findByIdForUpdate(id)
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
