package com.aaryanahmed.paymentsledger.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(@Schema(example = "100.00") @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount)
{ }
