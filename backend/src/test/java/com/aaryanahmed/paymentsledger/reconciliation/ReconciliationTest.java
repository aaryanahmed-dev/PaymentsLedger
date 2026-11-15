package com.aaryanahmed.paymentsledger.reconciliation;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReconciliationTest extends IntegrationTest {

    @Autowired
    private ReconciliationJob reconciliationJob;

    private long alice;
    private long bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = createAccount("Alice", "EUR");
        bob = createAccount("Bob", "EUR");
        deposit(alice, "100.00").andExpect(status().isCreated());
        transfer(alice, bob, "40.00").andExpect(status().isCreated());
    }

    @Test
    void healthyLedgerIsBalanced() throws Exception {
        mockMvc.perform(get("/admin/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.ledgerTotal").value(0))
                .andExpect(jsonPath("$.mismatches").isEmpty())
                .andExpect(jsonPath("$.accountsChecked").isNumber())
                .andExpect(jsonPath("$.entriesChecked").isNumber())
                .andExpect(jsonPath("$.checkedAt").exists());
    }

    @Test
    void reportsAnAccountWhoseBalanceDoesNotMatchItsEntries() throws Exception {
        // Corrupt the data behind the application's back, the way a bug or a manual fix might.
        jdbc.update("UPDATE accounts SET balance = balance + 5 WHERE id = ?", bob);
        try {
            mockMvc.perform(get("/admin/reconciliation"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balanced").value(false))
                    .andExpect(jsonPath("$.ledgerTotal").value(0))
                    .andExpect(jsonPath("$.mismatches.length()").value(1))
                    .andExpect(jsonPath("$.mismatches[0].accountId").value(bob))
                    .andExpect(jsonPath("$.mismatches[0].balance").value(45.00))
                    .andExpect(jsonPath("$.mismatches[0].entriesSum").value(40.00))
                    .andExpect(jsonPath("$.mismatches[0].difference").value(5.00));
        } finally {
            // Put it back: every test shares this database.
            jdbc.update("UPDATE accounts SET balance = balance - 5 WHERE id = ?", bob);
        }
    }

    @Test
    void reportsEntriesThatDoNotSumToZero() throws Exception {
        long entryId = jdbc.queryForObject(
                "SELECT MAX(id) FROM ledger_entries WHERE account_id = ?", Long.class, bob);
        jdbc.update("UPDATE ledger_entries SET amount = amount + 1 WHERE id = ?", entryId);
        try {
            mockMvc.perform(get("/admin/reconciliation"))
                    .andExpect(jsonPath("$.balanced").value(false))
                    .andExpect(jsonPath("$.ledgerTotal").value(1.00))
                    .andExpect(jsonPath("$.mismatches[0].accountId").value(bob))
                    .andExpect(jsonPath("$.mismatches[0].difference").value(-1.00));
        } finally {
            jdbc.update("UPDATE ledger_entries SET amount = amount - 1 WHERE id = ?", entryId);
        }
    }

    @Test
    void scheduledJobRunsWithoutError() {
        reconciliationJob.run();
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
