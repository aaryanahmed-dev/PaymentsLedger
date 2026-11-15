package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Phase 1 acceptance test: money moves correctly and the ledger invariants hold. */
class TransferFlowIntegrationTest extends IntegrationTest {

    @Test
    void depositThenTransferKeepsTheLedgerBalanced() throws Exception {
        long alice = createAccount("Alice", "EUR");
        long bob = createAccount("Bob", "EUR");

        deposit(alice, "100.00").andExpect(status().isCreated());
        transfer(alice, bob, "30.25").andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(69.75));
        mockMvc.perform(get("/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(30.25));

        // Alice has two entries, newest first: the transfer out, then the deposit in.
        mockMvc.perform(get("/accounts/{id}/entries", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(-30.25))
                .andExpect(jsonPath("$.content[1].amount").value(100.00));

        assertLedgerInvariants();
    }

    @Test
    void rejectedTransferChangesNothing() throws Exception {
        long alice = createAccount("Alice", "EUR");
        long bob = createAccount("Bob", "EUR");
        deposit(alice, "10.00").andExpect(status().isCreated());

        transfer(alice, bob, "10.01").andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(10.00));
        mockMvc.perform(get("/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(0));
        mockMvc.perform(get("/accounts/{id}/entries", bob)).andExpect(jsonPath("$.totalElements").value(0));
        assertLedgerInvariants();
    }
}
