package com.aaryanahmed.paymentsledger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConcurrentRunner {

    private ConcurrentRunner() {
    }

    public interface Task {
        Object run(int index) throws Exception;
    }

    /**
     * Runs the task once on each of {@code count} threads, released at the same instant,
     * and returns the exceptions thrown by the tasks that failed.
     */
    public static List<Throwable> runConcurrently(int count, Task task) throws Exception {
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
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }
}
