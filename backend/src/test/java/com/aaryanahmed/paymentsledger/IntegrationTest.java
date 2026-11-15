package com.aaryanahmed.paymentsledger;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: the full application running against a real MySQL container.
 *
 * Spring caches the application context between test classes with the same configuration,
 * so every class extending this shares ONE context and ONE database container.
 * Tests therefore create their own accounts instead of assuming an empty database.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    protected void assertLedgerInvariants() {
        LedgerInvariants.assertHold(jdbc);
    }

    protected long createAccount(String ownerName, String currency) throws Exception {
        String body = """
                {"ownerName": "%s", "currency": "%s"}
                """.formatted(ownerName, currency);
        return idOf(mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));
    }

    /** Deposits with a fresh idempotency key, as a well-behaved client would for each new request. */
    protected ResultActions deposit(long accountId, String amount) throws Exception {
        return deposit(accountId, amount, UUID.randomUUID().toString());
    }

    protected ResultActions deposit(long accountId, String amount, String idempotencyKey) throws Exception {
        String body = """
                {"amount": %s}
                """.formatted(amount);
        return mockMvc.perform(post("/accounts/{id}/deposits", accountId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions transfer(long fromAccountId, long toAccountId, String amount) throws Exception {
        return transfer(fromAccountId, toAccountId, amount, UUID.randomUUID().toString());
    }

    protected ResultActions transfer(long fromAccountId, long toAccountId, String amount,
                                     String idempotencyKey) throws Exception {
        String body = """
                {"fromAccountId": %d, "toAccountId": %d, "amount": %s}
                """.formatted(fromAccountId, toAccountId, amount);
        return mockMvc.perform(post("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions reverse(long transferId) throws Exception {
        return mockMvc.perform(post("/transfers/{id}/reverse", transferId));
    }

    protected BigDecimal balanceOf(long accountId) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    /** Reads the "id" field out of a JSON response body. */
    protected static long idOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
