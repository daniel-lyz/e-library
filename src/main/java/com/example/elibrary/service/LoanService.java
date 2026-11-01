package com.example.elibrary.service;

import com.example.elibrary.domain.Book;
import com.example.elibrary.domain.BookNotFoundException;
import com.example.elibrary.domain.BookUnavailableException;
import com.example.elibrary.mapper.BookMapper;
import com.example.elibrary.domain.AlreadyBorrowedException;
import com.example.elibrary.domain.Loan;
import com.example.elibrary.domain.LoanAlreadyReturnedException;
import com.example.elibrary.domain.LoanEvents;
import com.example.elibrary.domain.LoanLimitExceededException;
import com.example.elibrary.domain.LoanNotFoundException;
import com.example.elibrary.mapper.LoanDetails;
import com.example.elibrary.mapper.LoanMapper;
import com.example.elibrary.mapper.UserLoanQuotaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Borrow / return use cases.
 *
 * <h2>Concurrency</h2>
 * Correctness never relies on in-memory locks or on what was read earlier in the transaction:
 * <ul>
 *   <li>copies: {@code UPDATE book ... WHERE available_copies > 0} (no over-lending);</li>
 *   <li>loan limit: {@code UPDATE user_loan_quota ... WHERE active_count < max};</li>
 *   <li>duplicate loans: unique index {@code (user_id, book_id, active_flag)};</li>
 *   <li>double return: {@code UPDATE loan ... WHERE status = 'ACTIVE'}.</li>
 * </ul>
 * Each use case runs in one transaction, so a failure at any step rolls back the earlier ones.
 *
 * <h2>Lock order</h2>
 * Both use cases lock the user's quota row first (borrow: quota, book, loan; return: quota, loan, book).
 * Writes of the same user are therefore serialised at step one, and different users only meet on the
 * book row, which each transaction locks once, so no lock cycle (deadlock) can form between them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final BookMapper bookMapper;
    private final LoanMapper loanMapper;
    private final UserLoanQuotaMapper quotaMapper;
    private final LoanPolicy policy;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Transactional
    public LoanView borrow(long userId, long bookId) {
        Instant now = clock.instant();

        Book book = bookMapper.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
        book.assertBorrowable();

        // Fast path for a precise error message. Not a guarantee: the unique index below is.
        if (loanMapper.existsActive(userId, bookId)) {
            throw new AlreadyBorrowedException(bookId);
        }

        acquireLoanSlot(userId);

        if (bookMapper.decrementAvailable(bookId, now) == 0) {
            throw new BookUnavailableException(bookId);
        }

        Loan loan = Loan.open(userId, bookId, now, policy.getPeriod());
        try {
            loanMapper.insert(loan);
        } catch (DuplicateKeyException e) {
            // a concurrent request by the same user for the same book won the race
            throw new AlreadyBorrowedException(bookId);
        }

        events.publishEvent(new LoanEvents.BookBorrowed(loan.getId(), userId, bookId));
        return LoanView.of(loadDetails(loan.getId(), userId), now);
    }

    @Transactional
    public LoanView returnLoan(long userId, long loanId) {
        Instant now = clock.instant();

        // lock order: quota first (see class comment); rolled back below if the loan cannot be returned
        boolean slotReleased = quotaMapper.release(userId) == 1;

        if (loanMapper.markReturned(loanId, userId, now) == 0) {
            Loan existing = loanMapper.findByIdAndUser(loanId, userId)
                    .orElseThrow(() -> new LoanNotFoundException(loanId));
            throw new LoanAlreadyReturnedException(existing.getId());
        }

        Loan loan = loanMapper.findByIdAndUser(loanId, userId).orElseThrow(() -> new LoanNotFoundException(loanId));

        // The reader has given the book back; data drift must not stop that. Log loudly instead,
        // so reconciliation can fix the counter.
        if (!slotReleased) {
            log.error("invariant violated: no active loan slot to release, userId={}, loanId={}", userId, loanId);
        }
        if (bookMapper.incrementAvailable(loan.getBookId(), now) == 0) {
            log.error("invariant violated: book counter already full, bookId={}, loanId={}", loan.getBookId(), loanId);
        }

        events.publishEvent(new LoanEvents.BookReturned(loanId, userId, loan.getBookId()));
        return LoanView.of(loadDetails(loanId, userId), now);
    }

    @Transactional(readOnly = true)
    public LoanView getLoan(long userId, long loanId) {
        return LoanView.of(loadDetails(loanId, userId), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<LoanView> activeLoans(long userId) {
        Instant now = clock.instant();
        return loanMapper.findActiveDetails(userId).stream()
                .map(d -> LoanView.of(d, now))
                .collect(Collectors.toList());
    }

    /**
     * Reserves one of the user's loan slots. The quota row is created lazily on the first borrow.
     * <p>
     * A failed first attempt is ambiguous: either the limit is reached, or the row does not exist yet.
     * So the row is ensured (inserted here, or by a concurrent first borrow that won the primary key) and the
     * conditional update is retried <em>in every case</em>. Only a second failure means the limit is reached.
     * Skipping the retry when {@code exists} is true would be a check-then-act race: a concurrent request may
     * have created the row between the first update and the check, and the reader would be told
     * "limit exceeded" while holding no books at all.
     */
    private void acquireLoanSlot(long userId) {
        int max = policy.getMaxActiveLoans();
        if (quotaMapper.tryAcquire(userId, max) == 1) {
            return;
        }
        if (!quotaMapper.exists(userId)) {
            try {
                quotaMapper.insert(userId);
            } catch (DuplicateKeyException e) {
                log.debug("quota row for user {} created concurrently", userId);
            }
        }
        if (quotaMapper.tryAcquire(userId, max) == 1) {
            return;
        }
        throw new LoanLimitExceededException(max);
    }

    private LoanDetails loadDetails(long loanId, long userId) {
        return loanMapper.findDetails(loanId, userId).orElseThrow(() -> new LoanNotFoundException(loanId));
    }
}
