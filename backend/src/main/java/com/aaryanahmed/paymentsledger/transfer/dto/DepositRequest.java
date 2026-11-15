package com.aaryanahmed.paymentsledger.transfer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(@NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount)
{ }
