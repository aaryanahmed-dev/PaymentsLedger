package com.aaryanahmed.paymentsledger.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description shown at the top of Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentsLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payments Ledger API")
                .version("1.0")
                .description("A double-entry payments ledger: accounts, transfers, deposits, reversals, "
                        + "idempotency keys, signed webhooks and reconciliation. "
                        + "Errors are returned as RFC 9457 problem details."));
    }
}
