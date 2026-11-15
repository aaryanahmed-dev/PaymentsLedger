package com.aaryanahmed.paymentsledger;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two rules that must ALWAYS be true in a double-entry ledger, checked with plain SQL
 * so the check does not depend on the application code it is checking.
 */
public final class LedgerInvariants {

    private LedgerInvariants() {
    }

    public static void assertHold(JdbcTemplate jdbc) {
        // 1. Money is never created or destroyed: every transfer writes +x and -x.
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries", BigDecimal.class);
        assertThat(total).as("sum of all ledger entries").isEqualByComparingTo(BigDecimal.ZERO);

        // 2. Each stored balance equals the sum of that account's entries.
        List<String> mismatches = jdbc.query("""
                SELECT a.id, a.balance, COALESCE(SUM(e.amount), 0) AS entry_sum
                FROM accounts a
                LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY a.id, a.balance
                HAVING a.balance <> COALESCE(SUM(e.amount), 0)
                """,
                (rs, rowNum) -> "account %d: balance %s but entries sum to %s".formatted(
                        rs.getLong("id"), rs.getBigDecimal("balance"), rs.getBigDecimal("entry_sum")));
        assertThat(mismatches).as("accounts whose balance differs from their entries").isEmpty();
    }
}
