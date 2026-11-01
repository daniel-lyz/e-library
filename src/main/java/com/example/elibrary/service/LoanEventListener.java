package com.example.elibrary.service;

import com.example.elibrary.domain.LoanEvents;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only after the transaction commits, so a rolled-back borrow never produces an event.
 * <p>
 * In-process events are not durable: if the process dies between commit and this call, the event is lost.
 * That is fine for logs and metrics. Anything that must not be lost (notifications, downstream services)
 * should use a transactional outbox table relayed to a message queue instead.
 * <p>
 * Logs carry ids only, never book titles next to user ids: reading history is personal data.
 */
@Slf4j
@Component
public class LoanEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBorrowed(LoanEvents.BookBorrowed event) {
        log.info("book borrowed: loanId={}, userId={}, bookId={}", event.getLoanId(), event.getUserId(), event.getBookId());
        Metrics.counter("elibrary.loans", "event", "borrowed").increment();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReturned(LoanEvents.BookReturned event) {
        log.info("book returned: loanId={}, userId={}, bookId={}", event.getLoanId(), event.getUserId(), event.getBookId());
        Metrics.counter("elibrary.loans", "event", "returned").increment();
    }
}
