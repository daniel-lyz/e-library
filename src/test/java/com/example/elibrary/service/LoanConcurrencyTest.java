package com.example.elibrary.service;

import com.example.elibrary.domain.BookUnavailableException;
import com.example.elibrary.domain.AlreadyBorrowedException;
import com.example.elibrary.domain.LoanAlreadyReturnedException;
import com.example.elibrary.domain.LoanLimitExceededException;
import com.example.elibrary.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many threads hit the same rows at the same moment; the invariants must hold afterwards.
 * Runs against H2 here; the same SQL relies only on "single UPDATE is atomic" and "unique index",
 * which behave the same on MySQL/InnoDB.
 */
class LoanConcurrencyTest extends IntegrationTest {

    private static final String OK = "OK";

    @Autowired
    private LoanService loanService;

    @Test
    void onlyOneOfManyBorrowersGetsTheLastCopy() throws Exception {
        long book = newBook(1);

        Map<String, Long> outcomes = runConcurrently(50, i -> {
            long user = newUserId();
            return () -> loanService.borrow(user, book);
        });

        assertThat(outcomes).containsOnlyKeys(OK, BookUnavailableException.class.getSimpleName());
        assertThat(outcomes.get(OK)).isEqualTo(1);
        assertThat(availableCopies(book)).isZero();
        assertThat(activeLoansOfBook(book)).isEqualTo(1);
    }

    @Test
    void copiesAreNeverOverLent() throws Exception {
        long book = newBook(20);

        Map<String, Long> outcomes = runConcurrently(60, i -> {
            long user = newUserId();
            return () -> loanService.borrow(user, book);
        });

        assertThat(outcomes).containsOnlyKeys(OK, BookUnavailableException.class.getSimpleName());
        assertThat(outcomes.get(OK)).isEqualTo(20);
        assertThat(availableCopies(book)).isZero();
        assertThat(activeLoansOfBook(book)).isEqualTo(20);
    }

    @Test
    void loanLimitHoldsWhenOneUserBorrowsManyBooksAtOnce() throws Exception {
        long user = newUserId();   // brand-new user: the quota row itself is created under contention
        List<Long> books = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            books.add(newBook(3));
        }

        Map<String, Long> outcomes = runConcurrently(books.size(), i -> () -> loanService.borrow(user, books.get(i)));

        assertThat(outcomes).containsOnlyKeys(OK, LoanLimitExceededException.class.getSimpleName());
        assertThat(outcomes.get(OK)).isEqualTo(5);
        assertThat(activeLoansOfUser(user)).isEqualTo(5);
        assertThat(quotaOf(user)).isEqualTo(5);
        long copiesTaken = books.stream().mapToLong(b -> 3 - availableCopies(b)).sum();
        assertThat(copiesTaken).isEqualTo(5);
    }

    @Test
    void sameUserSameBookConcurrentlyYieldsOneLoan() throws Exception {
        long user = newUserId();
        long book = newBook(10);

        Map<String, Long> outcomes = runConcurrently(10, i -> () -> loanService.borrow(user, book));

        assertThat(outcomes).containsOnlyKeys(OK, AlreadyBorrowedException.class.getSimpleName());
        assertThat(outcomes.get(OK)).isEqualTo(1);
        assertThat(availableCopies(book)).isEqualTo(9);
        assertThat(quotaOf(user)).isEqualTo(1);
    }

    @Test
    void concurrentReturnsOfOneLoanPutBackExactlyOneCopy() throws Exception {
        long user = newUserId();
        long book = newBook(1);
        long loanId = loanService.borrow(user, book).getLoanId();

        Map<String, Long> outcomes = runConcurrently(10, i -> () -> loanService.returnLoan(user, loanId));

        assertThat(outcomes).containsOnlyKeys(OK, LoanAlreadyReturnedException.class.getSimpleName());
        assertThat(outcomes.get(OK)).isEqualTo(1);
        assertThat(availableCopies(book)).isEqualTo(1);
        assertThat(quotaOf(user)).isZero();
    }

    /**
     * Starts all tasks at the same instant and counts outcomes: "OK" or the simple name of the exception thrown.
     * Any unexpected exception type shows up in the map and fails the {@code containsOnlyKeys} assertion.
     */
    private Map<String, Long> runConcurrently(int threads, IntFunction<Callable<?>> taskFactory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<?> task = taskFactory.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return OK;
                    } catch (Exception e) {
                        return e.getClass().getSimpleName();
                    }
                }));
            }
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                try {
                    results.add(f.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    results.add(e.getCause().getClass().getSimpleName());
                }
            }
            return results.stream().collect(Collectors.groupingBy(r -> r, Collectors.counting()));
        } finally {
            pool.shutdownNow();
        }
    }
}
