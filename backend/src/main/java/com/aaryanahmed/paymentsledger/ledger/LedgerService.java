package com.aaryanahmed.paymentsledger.ledger;

import com.aaryanahmed.paymentsledger.account.AccountNotFoundException;
import com.aaryanahmed.paymentsledger.account.AccountRepository;
import com.aaryanahmed.paymentsledger.common.PageResponse;
import com.aaryanahmed.paymentsledger.ledger.dto.LedgerEntryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    // Newest first. The id breaks ties between entries created in the same second.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final LedgerEntryRepository entryRepo;
    private final AccountRepository accountRepo;

    public LedgerService(LedgerEntryRepository entryRepo, AccountRepository accountRepo) {
        this.entryRepo = entryRepo;
        this.accountRepo = accountRepo;
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> getEntries(Long accountId, int page, int size) {
        if (!accountRepo.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return PageResponse.from(
                entryRepo.findByAccountId(accountId, PageRequest.of(page, size, NEWEST_FIRST))
                        .map(LedgerEntryResponse::from));
    }
}
