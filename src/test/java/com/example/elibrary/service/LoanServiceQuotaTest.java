package com.example.elibrary.service;

import com.example.elibrary.domain.Book;
import com.example.elibrary.domain.BookStatus;
import com.example.elibrary.domain.Loan;
import com.example.elibrary.domain.LoanLimitExceededException;
import com.example.elibrary.domain.LoanStatus;
import com.example.elibrary.mapper.BookMapper;
import com.example.elibrary.mapper.LoanDetails;
import com.example.elibrary.mapper.LoanMapper;
import com.example.elibrary.mapper.UserLoanQuotaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lazy creation of the per-user quota row, with each concurrent interleaving reproduced deterministically.
 * (A stress test hits these windows only by luck; mocks hit them every time.)
 */
class LoanServiceQuotaTest {

    private static final long USER = 7L;
    private static final long BOOK = 42L;
    private static final int MAX = 5;

    private final BookMapper bookMapper = mock(BookMapper.class);
    private final LoanMapper loanMapper = mock(LoanMapper.class);
    private final UserLoanQuotaMapper quotaMapper = mock(UserLoanQuotaMapper.class);
    private LoanService service;

    @BeforeEach
    void setUp() {
        LoanPolicy policy = new LoanPolicy();
        policy.setMaxActiveLoans(MAX);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new LoanService(bookMapper, loanMapper, quotaMapper, policy, clock, mock(ApplicationEventPublisher.class));

        Book book = Book.builder().id(BOOK).status(BookStatus.ON_SHELF).totalCopies(3).availableCopies(3).build();
        when(bookMapper.findById(BOOK)).thenReturn(Optional.of(book));
        when(loanMapper.existsActive(USER, BOOK)).thenReturn(false);
        when(bookMapper.decrementAvailable(eq(BOOK), any())).thenReturn(1);
        // like MyBatis useGeneratedKeys: the insert fills in the generated id
        doAnswer(inv -> {
            Loan inserted = inv.getArgument(0);
            ReflectionTestUtils.setField(inserted, "id", 1L);
            return null;
        }).when(loanMapper).insert(any(Loan.class));

        LoanDetails details = mock(LoanDetails.class);
        when(details.toLoan()).thenReturn(Loan.builder()
                .id(1L).userId(USER).bookId(BOOK).status(LoanStatus.ACTIVE)
                .borrowedAt(clock.instant()).dueAt(clock.instant().plus(Duration.ofDays(14)))
                .build());
        when(loanMapper.findDetails(anyLong(), eq(USER))).thenReturn(Optional.of(details));
    }

    @Test
    void firstBorrowCreatesTheQuotaRowThenReservesASlot() {
        when(quotaMapper.tryAcquire(USER, MAX)).thenReturn(0, 1);
        when(quotaMapper.exists(USER)).thenReturn(false);

        assertThatCode(() -> service.borrow(USER, BOOK)).doesNotThrowAnyException();
        verify(quotaMapper).insert(USER);
    }

    /**
     * The regression: a concurrent first borrow created (and committed) the row between our first update and
     * the existence check. That is not "limit reached"; the update must be retried.
     */
    @Test
    void rowCreatedConcurrentlyBetweenUpdateAndCheckIsNotReportedAsLimitReached() {
        when(quotaMapper.tryAcquire(USER, MAX)).thenReturn(0, 1);
        when(quotaMapper.exists(USER)).thenReturn(true);

        assertThatCode(() -> service.borrow(USER, BOOK)).doesNotThrowAnyException();
        verify(quotaMapper, never()).insert(anyLong());
    }

    @Test
    void losingTheRaceToInsertTheRowStillReservesASlot() {
        when(quotaMapper.tryAcquire(USER, MAX)).thenReturn(0, 1);
        when(quotaMapper.exists(USER)).thenReturn(false);
        doThrow(new DuplicateKeyException("uk")).when(quotaMapper).insert(USER);

        assertThatCode(() -> service.borrow(USER, BOOK)).doesNotThrowAnyException();
    }

    @Test
    void limitReachedWhenTheRetryAlsoFails() {
        when(quotaMapper.tryAcquire(USER, MAX)).thenReturn(0, 0);
        when(quotaMapper.exists(USER)).thenReturn(true);

        assertThatThrownBy(() -> service.borrow(USER, BOOK)).isInstanceOf(LoanLimitExceededException.class);
        verify(bookMapper, never()).decrementAvailable(anyLong(), any());
    }

    @Test
    void existingRowWithFreeSlotNeedsNoExtraQueries() {
        when(quotaMapper.tryAcquire(USER, MAX)).thenReturn(1);

        assertThatCode(() -> service.borrow(USER, BOOK)).doesNotThrowAnyException();
        verify(quotaMapper, never()).exists(anyLong());
        verify(quotaMapper, never()).insert(anyLong());
    }
}
