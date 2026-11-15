package com.aaryanahmed.paymentsledger.transfer;

import com.aaryanahmed.paymentsledger.IntegrationTest;
import com.aaryanahmed.paymentsledger.account.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fires many transfers at the same accounts at the same moment.
 * Without row locks these tests fail: balances are overwritten, accounts are overdrawn
 * and opposite-direction transfers deadlock.
 */
class TransferConcurrencyTest extends IntegrationTest {

    @Autowired
    private TransferService transferService;

    @Test
    void concurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
        long alice = createAccount("Alice", "EUR");
        long bob = createAccount("Bob", "EUR");
        deposit(alice, "100.00").andExpect(status().isCreated());

        // 100 withdrawals of 10.00 from a balance of 100.00: exactly 10 can succeed.
        List<Throwable> failures = runConcurrently(100,
                i -> transferService.transfer(alice, bob, new BigDecimal("10.00")));

        assertThat(failures).hasSize(90).allMatch(f -> f instanceof InsufficientFundsException);
        assertThat(balanceOf(alice)).isEqualByComparingTo("0");
        assertThat(balanceOf(bob)).isEqualByComparingTo("100.00");
        assertLedgerInvariants();
    }

    @Test
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        long alice = createAccount("Alice", "EUR");
        long bob = createAccount("Bob", "EUR");
        deposit(alice, "100.00").andExpect(status().isCreated());
        deposit(bob, "100.00").andExpect(status().isCreated());

        // Even tasks send Alice -> Bob, odd tasks send Bob -> Alice.
        List<Throwable> failures = runConcurrently(100, i -> {
            if (i % 2 == 0) {
                transferService.transfer(alice, bob, new BigDecimal("1.00"));
            } else {
                transferService.transfer(bob, alice, new BigDecimal("1.00"));
            }
            return null;
        });

        assertThat(failures).isEmpty();
        // 50 transfers of 1.00 each way cancel out.
        assertThat(balanceOf(alice)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(bob)).isEqualByComparingTo("100.00");
        assertLedgerInvariants();
    }

    @Test
    void concurrentDepositsAreAllCounted() throws Exception {
        long alice = createAccount("Alice", "EUR");

        List<Throwable> failures = runConcurrently(50,
                i -> transferService.deposit(alice, new BigDecimal("1.00")));

        assertThat(failures).isEmpty();
        assertThat(balanceOf(alice)).isEqualByComparingTo("50.00");
        assertLedgerInvariants();
    }

    private BigDecimal balanceOf(long accountId) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private interface Task {
        Object run(int index) throws Exception;
    }

    /**
     * Runs the task once on each of {@code count} threads, released at the same instant,
     * and returns the exceptions thrown by the tasks that failed.
     */
    private static List<Throwable> runConcurrently(int count, Task task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<Object> job = () -> {
                    ready.countDown();
                    start.await();          // every thread waits here...
                    return task.run(index);
                };
                futures.add(executor.submit(job));
            }
            ready.await();
            start.countDown();              // ...until this releases them all at once

            List<Throwable> failures = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }
}
