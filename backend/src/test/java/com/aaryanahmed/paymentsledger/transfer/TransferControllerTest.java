package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerTest extends IntegrationTest {

    private static final long UNKNOWN_ID = Long.MAX_VALUE;

    private long alice;
    private long bob;

    @BeforeEach
    void setUp() throws Exception {
        alice = createAccount("Alice", "EUR");
        bob = createAccount("Bob", "EUR");
        deposit(alice, "100.00").andExpect(status().isCreated());
    }

    // Whatever a test did, valid or rejected, the ledger must still balance afterwards.
    @AfterEach
    void ledgerStillBalances() {
        assertLedgerInvariants();
    }

    // ---- POST /transfers ----

    @Test
    void createsTransfer() throws Exception {
        transfer(alice, bob, "40.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fromAccountId").value(alice))
                .andExpect(jsonPath("$.toAccountId").value(bob))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(60.00));
        mockMvc.perform(get("/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(40.00));
    }

    @Test
    void canTransferTheWholeBalance() throws Exception {
        transfer(alice, bob, "100.00").andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void insufficientFundsIs422() throws Exception {
        transfer(alice, bob, "100.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());

        mockMvc.perform(get("/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void currencyMismatchIs422() throws Exception {
        long dollarAccount = createAccount("Dave", "USD");

        transfer(alice, dollarAccount, "10.00").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownSenderIs404() throws Exception {
        transfer(UNKNOWN_ID, bob, "10.00").andExpect(status().isNotFound());
    }

    @Test
    void unknownReceiverIs404() throws Exception {
        transfer(alice, UNKNOWN_ID, "10.00").andExpect(status().isNotFound());
    }

    @Test
    void sameAccountIs400() throws Exception {
        transfer(alice, alice, "10.00").andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00", "10.001", "\"abc\"", "null"})
    void invalidAmountIs400(String amount) throws Exception {
        transfer(alice, bob, amount).andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsAre400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/transfers").header("Idempotency-Key", "missing-fields-test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fromAccountId").exists())
                .andExpect(jsonPath("$.errors.toAccountId").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    // ---- GET /transfers/{id} ----

    @Test
    void getsTransferById() throws Exception {
        long transferId = idOf(transfer(alice, bob, "12.34"));

        mockMvc.perform(get("/transfers/{id}", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId))
                .andExpect(jsonPath("$.fromAccountId").value(alice))
                .andExpect(jsonPath("$.toAccountId").value(bob))
                .andExpect(jsonPath("$.amount").value(12.34));
    }

    @Test
    void unknownTransferIs404() throws Exception {
        mockMvc.perform(get("/transfers/{id}", UNKNOWN_ID)).andExpect(status().isNotFound());
    }

    // ---- POST /accounts/{id}/deposits ----

    @Test
    void depositComesFromTheFundingAccountOfTheSameCurrency() throws Exception {
        long fundingAccount = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE account_type = 'EXTERNAL' AND currency = 'EUR'", Long.class);

        deposit(bob, "25.50")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccountId").value(fundingAccount))
                .andExpect(jsonPath("$.toAccountId").value(bob))
                .andExpect(jsonPath("$.amount").value(25.50))
                .andExpect(jsonPath("$.currency").value("EUR"));

        mockMvc.perform(get("/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(25.50));
    }

    @Test
    void depositToUnknownAccountIs404() throws Exception {
        deposit(UNKNOWN_ID, "10.00").andExpect(status().isNotFound());
    }

    @Test
    void depositInCurrencyWithoutFundingAccountIs422() throws Exception {
        long yenAccount = createAccount("Yuki", "JPY");

        deposit(yenAccount, "10.00").andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00", "10.001", "null"})
    void invalidDepositAmountIs400(String amount) throws Exception {
        deposit(bob, amount).andExpect(status().isBadRequest());
    }
}
