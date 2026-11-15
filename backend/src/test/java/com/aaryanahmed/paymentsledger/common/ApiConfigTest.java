package com.aaryanahmed.paymentsledger.common;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiConfigTest extends IntegrationTest {

    @Test
    void openApiDescriptionListsTheEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Payments Ledger API"))
                .andExpect(jsonPath("$.paths['/accounts']").exists())
                .andExpect(jsonPath("$.paths['/transfers'].post").exists())
                .andExpect(jsonPath("$.paths['/transfers/{id}/reverse'].post").exists())
                .andExpect(jsonPath("$.paths['/accounts/{id}/deposits'].post").exists())
                .andExpect(jsonPath("$.paths['/accounts/{id}/entries'].get").exists())
                .andExpect(jsonPath("$.paths['/webhook-endpoints']").exists())
                .andExpect(jsonPath("$.paths['/admin/reconciliation'].get").exists());
    }

    // A browser sends this "preflight" OPTIONS request before a cross-origin POST.
    @Test
    void allowedOriginMayCallTheApi() throws Exception {
        mockMvc.perform(options("/transfers")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mockMvc.perform(options("/transfers")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
