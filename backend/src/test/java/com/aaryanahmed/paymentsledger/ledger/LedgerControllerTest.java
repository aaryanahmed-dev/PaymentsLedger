package com.aaryanahmed.paymentsledger.ledger;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerControllerTest extends IntegrationTest {

    @Test
    void newAccountHasNoEntries() throws Exception {
        long alice = createAccount("Alice", "EUR");

        mockMvc.perform(get("/accounts/{id}/entries", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void entriesAreSignedAndLinkedToTheirTransfer() throws Exception {
        long alice = createAccount("Alice", "EUR");
        long bob = createAccount("Bob", "EUR");
        deposit(alice, "50.00");
        long transferId = idOf(transfer(alice, bob, "20.00"));

        // Sender sees a negative entry, receiver a positive one, both for the same transfer.
        mockMvc.perform(get("/accounts/{id}/entries", alice))
                .andExpect(jsonPath("$.content[0].amount").value(-20.00))
                .andExpect(jsonPath("$.content[0].transferId").value(transferId))
                .andExpect(jsonPath("$.content[0].accountId").value(alice));
        mockMvc.perform(get("/accounts/{id}/entries", bob))
                .andExpect(jsonPath("$.content[0].amount").value(20.00))
                .andExpect(jsonPath("$.content[0].transferId").value(transferId));
    }

    @Test
    void entriesArePaginatedNewestFirst() throws Exception {
        long alice = createAccount("Alice", "EUR");
        deposit(alice, "1.00");
        deposit(alice, "2.00");
        deposit(alice, "3.00");

        mockMvc.perform(get("/accounts/{id}/entries", alice).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(3.00))
                .andExpect(jsonPath("$.content[1].amount").value(2.00))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/accounts/{id}/entries", alice).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(1.00));
    }

    @Test
    void entriesOfUnknownAccountAre404() throws Exception {
        mockMvc.perform(get("/accounts/{id}/entries", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "size=abc"})
    void invalidPagingIs400(String query) throws Exception {
        long alice = createAccount("Alice", "EUR");

        mockMvc.perform(get("/accounts/" + alice + "/entries?" + query))
                .andExpect(status().isBadRequest());
    }
}
