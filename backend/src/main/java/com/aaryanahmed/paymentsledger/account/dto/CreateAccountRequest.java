package com.aaryanahmed.paymentsledger.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(@Schema(example = "Alice Martin") @NotBlank @Size(max = 100) String ownerName,
                                   @Schema(example = "EUR") @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code like EUR")String currency)
{ }
