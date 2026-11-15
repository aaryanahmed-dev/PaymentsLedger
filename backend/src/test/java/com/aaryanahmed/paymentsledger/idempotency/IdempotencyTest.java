package com.aaryanahmed.paymentsledger.idempotency;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.aaryanahmed.paymentsledger.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyTest extends IntegrationTest {

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
    void missingKeyIs400() throws Exception {
        mockMvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": %d, "toAccountId": %d, "amount": 10.00}
                                """.formatted(alice, bob)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/accounts/{id}/deposits", alice).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 10.00}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void blankOrOverlongKeyIs400() throws Exception {
        transfer(alice, bob, "10.00", " ").andExpect(status().isBadRequest());
        transfer(alice, bob, "10.00", "k".repeat(256)).andExpect(status().isBadRequest());

        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
    }

    @Test
    void repeatedTransferReturnsTheOriginalResponseAndMovesMoneyOnce() throws Exception {
        String key = newKey();

        String first = transfer(alice, bob, "10.00", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = transfer(alice, bob, "10.00", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(balanceOf(alice)).isEqualByComparingTo("90.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("10.00");
        assertThat(transfersBetween(alice, bob)).isEqualTo(1);
    }

    @Test
    void repeatedDepositReturnsTheOriginalResponseAndMovesMoneyOnce() throws Exception {
        String key = newKey();

        String first = deposit(bob, "25.00", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = deposit(bob, "25.00", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(balanceOf(bob)).isEqualByComparingTo("25.00");
    }

    @Test
    void sameAmountWrittenDifferentlyIsTheSameRequest() throws Exception {
        String key = newKey();

        transfer(alice, bob, "10.5", key).andExpect(status().isCreated());
        transfer(alice, bob, "10.50", key).andExpect(status().isCreated());

        assertThat(balanceOf(bob)).isEqualByComparingTo("10.50");
    }

    @Test
    void sameKeyWithDifferentBodyIs409() throws Exception {
        String key = newKey();
        transfer(alice, bob, "10.00", key).andExpect(status().isCreated());

        transfer(alice, bob, "20.00", key).andExpect(status().isConflict());
        transfer(bob, alice, "10.00", key).andExpect(status().isConflict());

        assertThat(balanceOf(alice)).isEqualByComparingTo("90.00");
    }

    @Test
    void sameKeyOnADifferentEndpointIs409() throws Exception {
        String key = newKey();
        deposit(alice, "10.00", key).andExpect(status().isCreated());

        deposit(bob, "10.00", key).andExpect(status().isConflict());
        transfer(alice, bob, "10.00", key).andExpect(status().isConflict());

        assertThat(balanceOf(bob)).isEqualByComparingTo("0");
    }

    @Test
    void rejectedRequestDoesNotUseUpTheKey() throws Exception {
        String key = newKey();
        transfer(alice, bob, "150.00", key).andExpect(status().isUnprocessableEntity());

        // After topping up, the client can retry the very same request with the very same key.
        deposit(alice, "100.00").andExpect(status().isCreated());
        transfer(alice, bob, "150.00", key).andExpect(status().isCreated());

        assertThat(balanceOf(bob)).isEqualByComparingTo("150.00");
    }

    @Test
    void concurrentDuplicatesCreateOneTransfer() throws Exception {
        String key = newKey();
        Queue<MockHttpServletResponse> responses = new ConcurrentLinkedQueue<>();

        // 20 copies of one request arriving at the same moment, like an impatient client retrying.
        List<Throwable> failures = runConcurrently(20,
                i -> responses.add(transfer(alice, bob, "10.00", key).andReturn().getResponse()));

        assertThat(failures).isEmpty();
        assertThat(responses).hasSize(20).allMatch(response -> response.getStatus() == 201);
        assertThat(responses.stream().map(IdempotencyTest::body).distinct()).hasSize(1);
        assertThat(transfersBetween(alice, bob)).isEqualTo(1);
        assertThat(balanceOf(alice)).isEqualByComparingTo("90.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("10.00");
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String body(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int transfersBetween(long from, long to) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE from_account_id = ? AND to_account_id = ?",
                Integer.class, from, to);
    }
}
