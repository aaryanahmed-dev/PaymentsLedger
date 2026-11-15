package com.aaryanahmed.paymentsledger.reconciliation;

import com.aaryanahmed.paymentsledger.reconciliation.dto.AccountMismatch;
import com.aaryanahmed.paymentsledger.reconciliation.dto.ReconciliationReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Checks the two ledger invariants over the whole database:
 * all entries sum to zero, and every account's balance equals the sum of its entries.
 */
@Service
public class ReconciliationService {

    // These are reporting queries over whole tables, so plain SQL is clearer than JPA here.
    private static final String MISMATCHES_SQL = """
            SELECT a.id, a.balance, COALESCE(SUM(e.amount), 0) AS entries_sum
            FROM accounts a
            LEFT JOIN ledger_entries e ON e.account_id = a.id
            GROUP BY a.id, a.balance
            HAVING a.balance <> COALESCE(SUM(e.amount), 0)
            ORDER BY a.id
            """;

    private final JdbcTemplate jdbc;

    public ReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The read-only transaction matters: under MySQL's REPEATABLE READ all four queries
     * see the same snapshot of the database. Without it, a transfer committing between two
     * queries could make a perfectly healthy ledger look inconsistent.
     */
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile() {
        BigDecimal ledgerTotal = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries", BigDecimal.class);
        long entries = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long.class);
        long accounts = jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Long.class);
        List<AccountMismatch> mismatches = jdbc.query(MISMATCHES_SQL, (rs, rowNum) -> {
            BigDecimal balance = rs.getBigDecimal("balance");
            BigDecimal entriesSum = rs.getBigDecimal("entries_sum");
            return new AccountMismatch(rs.getLong("id"), balance, entriesSum, balance.subtract(entriesSum));
        });

        boolean balanced = ledgerTotal.compareTo(BigDecimal.ZERO) == 0 && mismatches.isEmpty();
        return new ReconciliationReport(Instant.now(), balanced, ledgerTotal, accounts, entries, mismatches);
    }
}
