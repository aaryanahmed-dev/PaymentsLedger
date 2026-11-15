package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.aaryanahmed.paymentsledger.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferReversalTest extends IntegrationTest {

    private long alice;
    private long bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = createAccount("Alice", "EUR");
        bob = createAccount("Bob", "EUR");
        deposit(alice, "100.00").andExpect(status().isCreated());
    }

    @AfterEach
    void ledgerStillBalances() {
        assertLedgerInvariants();
    }

    @Test
    void reversalCreatesTheOppositeTransferLinkedToTheOriginal() throws Exception {
        long original = idOf(transfer(alice, bob, "40.00"));

        long reversal = idOf(reverse(original)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccountId").value(bob))
                .andExpect(jsonPath("$.toAccountId").value(alice))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.reversalOf").value(original)));

        assertThat(reversal).isNotEqualTo(original);
        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("0");
        // The history is kept: Bob has the original entry and the reversing entry.
        mockMvc.perform(get("/accounts/{id}/entries", bob))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(-40.00))
                .andExpect(jsonPath("$.content[1].amount").value(40.00));
        // An ordinary transfer has no reversalOf.
        mockMvc.perform(get("/transfers/{id}", original)).andExpect(jsonPath("$.reversalOf").isEmpty());
    }

    @Test
    void depositCanBeReversed() throws Exception {
        long depositId = idOf(deposit(bob, "30.00"));

        reverse(depositId).andExpect(status().isCreated());

        assertThat(balanceOf(bob)).isEqualByComparingTo("0");
    }

    @Test
    void transferCanBeReversedOnlyOnce() throws Exception {
        long original = idOf(transfer(alice, bob, "40.00"));
        reverse(original).andExpect(status().isCreated());

        reverse(original).andExpect(status().isConflict());

        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void reversalCannotItselfBeReversed() throws Exception {
        long original = idOf(transfer(alice, bob, "40.00"));
        long reversal = idOf(reverse(original));

        reverse(reversal).andExpect(status().isConflict());
    }

    @Test
    void unknownTransferIs404() throws Exception {
        reverse(Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    @Test
    void reversalRespectsInsufficientFunds() throws Exception {
        long carol = createAccount("Carol", "EUR");
        long original = idOf(transfer(alice, bob, "40.00"));
        transfer(bob, carol, "30.00").andExpect(status().isCreated());   // Bob spends most of it

        reverse(original).andExpect(status().isUnprocessableEntity());

        assertThat(balanceOf(bob)).isEqualByComparingTo("10.00");
        // The failed attempt did not use up the one allowed reversal.
        transfer(carol, bob, "30.00").andExpect(status().isCreated());
        reverse(original).andExpect(status().isCreated());
    }

    @Test
    void concurrentReversalsOfOneTransferSucceedExactlyOnce() throws Exception {
        long original = idOf(transfer(alice, bob, "40.00"));
        Queue<Integer> statuses = new ConcurrentLinkedQueue<>();

        List<Throwable> failures = runConcurrently(10,
                i -> statuses.add(reverse(original).andReturn().getResponse().getStatus()));

        assertThat(failures).isEmpty();
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(9);
        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("0");
    }
}
