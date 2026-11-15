package com.aaryanahmed.paymentsledger.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: no Spring, no database, so they run in milliseconds. */
class AccountTest {

    @Test
    void newAccountStartsAsCustomerWithZeroBalance() {
        Account account = new Account("Alice", "EUR");

        assertThat(account.getBalance()).isEqualByComparingTo("0");
        assertThat(account.getAccountType()).isEqualTo(AccountType.CUSTOMER);
    }

    @Test
    void creditIncreasesBalance() {
        Account account = new Account("Alice", "EUR");

        account.credit(new BigDecimal("10.50"));
        account.credit(new BigDecimal("0.25"));

        assertThat(account.getBalance()).isEqualByComparingTo("10.75");
    }

    @Test
    void debitDecreasesBalance() {
        Account account = customerWithBalance("10.00");

        account.debit(new BigDecimal("3.99"));

        assertThat(account.getBalance()).isEqualByComparingTo("6.01");
    }

    @Test
    void customerCanSpendTheirWholeBalance() {
        Account account = customerWithBalance("10.00");

        account.debit(new BigDecimal("10.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void customerCannotGoNegative() {
        Account account = customerWithBalance("10.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        // A rejected debit must leave the balance untouched.
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void externalAccountCanGoNegative() {
        Account funding = new Account("External funding (EUR)", "EUR");
        // Production code never creates EXTERNAL accounts (they come from migrations),
        // so the test sets the type directly.
        ReflectionTestUtils.setField(funding, "accountType", AccountType.EXTERNAL);

        funding.debit(new BigDecimal("500.00"));

        assertThat(funding.getBalance()).isEqualByComparingTo("-500.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100"})
    void debitRejectsNonPositiveAmounts(String amount) {
        Account account = customerWithBalance("10.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal(amount)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100"})
    void creditRejectsNonPositiveAmounts(String amount) {
        Account account = customerWithBalance("10.00");

        assertThatThrownBy(() -> account.credit(new BigDecimal(amount)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void nullAmountIsRejected() {
        Account account = customerWithBalance("10.00");

        assertThatThrownBy(() -> account.debit(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Account customerWithBalance(String balance) {
        Account account = new Account("Alice", "EUR");
        account.credit(new BigDecimal(balance));
        return account;
    }
}
