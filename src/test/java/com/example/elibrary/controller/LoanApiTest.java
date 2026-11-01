package com.example.elibrary.controller;

import com.example.elibrary.domain.BookStatus;
import com.example.elibrary.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanApiTest extends IntegrationTest {

    private static final String USER = "X-User-Id";

    @Autowired
    private ObjectMapper objectMapper;

    // ---------------------------------------------------------------- borrow

    @Test
    void borrowCreatesLoanAndTakesACopy() throws Exception {
        long user = newUserId();
        long book = newBook(2);

        borrow(user, book)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/loans/\\d+")))
                .andExpect(jsonPath("$.bookId").value(book))
                .andExpect(jsonPath("$.bookTitle").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.returnedAt").doesNotExist());

        assertThat(availableCopies(book)).isEqualTo(1);
        assertThat(quotaOf(user)).isEqualTo(1);
    }

    @Test
    void dueDateFollowsLoanPeriod() throws Exception {
        long loanId = borrowOk(newUserId(), newBook(1));
        JsonNode loan = json(mockMvc.perform(get("/api/v1/loans/{id}", loanId).header(USER, ownerOf(loanId))));

        assertThat(java.time.Instant.parse(loan.get("dueAt").asText()))
                .isEqualTo(clock.instant().plus(Duration.ofDays(14)));
    }

    @Test
    void borrowRequiresIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/v1/loans").header(USER, "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void borrowValidatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/loans").header(USER, newUserId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("bookId"))
                // English regardless of the server's OS locale
                .andExpect(jsonPath("$.violations[0].message").value("must not be null"));

        mockMvc.perform(post("/api/v1/loans").header(USER, newUserId())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotBorrowTheSameBookTwice() throws Exception {
        long user = newUserId();
        long book = newBook(5);
        borrowOk(user, book);

        borrow(user, book)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BORROWED"));

        assertThat(availableCopies(book)).isEqualTo(4);
        assertThat(quotaOf(user)).isEqualTo(1);
    }

    @Test
    void cannotBorrowWhenNoCopiesLeft() throws Exception {
        long book = newBook(1);
        borrowOk(newUserId(), book);
        long secondUser = newUserId();

        borrow(secondUser, book)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_UNAVAILABLE"));

        assertThat(availableCopies(book)).isZero();
        // the failed attempt must not leak a quota slot
        assertThat(quotaOf(secondUser)).isZero();
    }

    @Test
    void cannotBorrowOffShelfBook() throws Exception {
        long book = newBook(3, BookStatus.OFF_SHELF);

        borrow(newUserId(), book)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_BORROWABLE"));
        assertThat(availableCopies(book)).isEqualTo(3);
    }

    @Test
    void existingLoansKeepWorkingAfterTheBookIsTakenOffTheShelf() throws Exception {
        long user = newUserId();
        long book = newBook(2);
        long loanId = borrowOk(user, book);

        jdbc.update("UPDATE book SET status = 'OFF_SHELF' WHERE id = ?", book);

        mockMvc.perform(get("/api/v1/me/loans").header(USER, user))
                .andExpect(jsonPath("$.items[0].id").value(loanId));
        returnLoan(user, loanId).andExpect(status().isOk());
        assertThat(availableCopies(book)).isEqualTo(2);
        assertThat(quotaOf(user)).isZero();
    }

    @Test
    void cannotBorrowUnknownBook() throws Exception {
        borrow(newUserId(), 999_999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void loanLimitIsEnforcedAndFreedByReturning() throws Exception {
        long user = newUserId();
        long firstLoan = 0;
        for (int i = 0; i < 5; i++) {
            long loanId = borrowOk(user, newBook(1));
            if (i == 0) {
                firstLoan = loanId;
            }
        }

        long sixth = newBook(1);
        borrow(user, sixth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_LIMIT_EXCEEDED"));
        assertThat(availableCopies(sixth)).isEqualTo(1);

        returnLoan(user, firstLoan).andExpect(status().isOk());
        borrow(user, sixth).andExpect(status().isCreated());
        assertThat(quotaOf(user)).isEqualTo(5);
    }

    // ---------------------------------------------------------------- return

    @Test
    void returnClosesLoanAndPutsCopyBack() throws Exception {
        long user = newUserId();
        long book = newBook(1);
        long loanId = borrowOk(user, book);

        returnLoan(user, loanId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnedAt").exists());

        assertThat(availableCopies(book)).isEqualTo(1);
        assertThat(quotaOf(user)).isZero();

        // the same user can borrow it again; history is kept
        borrow(user, book).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE user_id = ? AND book_id = ?",
                Integer.class, user, book)).isEqualTo(2);
    }

    @Test
    void returningTwiceIsRejectedAndChangesNothing() throws Exception {
        long user = newUserId();
        long book = newBook(1);
        long loanId = borrowOk(user, book);
        returnLoan(user, loanId).andExpect(status().isOk());

        returnLoan(user, loanId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_ALREADY_RETURNED"));

        assertThat(availableCopies(book)).isEqualTo(1);
        assertThat(quotaOf(user)).isZero();
    }

    @Test
    void cannotReturnOrSeeSomeoneElsesLoan() throws Exception {
        long owner = newUserId();
        long other = newUserId();
        long book = newBook(2);
        long loanId = borrowOk(owner, book);
        borrowOk(other, newBook(1));   // "other" has a quota row that must stay untouched

        returnLoan(other, loanId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/loans/{id}", loanId).header(USER, other))
                .andExpect(status().isNotFound());

        assertThat(availableCopies(book)).isEqualTo(1);
        assertThat(quotaOf(other)).isEqualTo(1);
        assertThat(activeLoansOfUser(owner)).isEqualTo(1);
    }

    @Test
    void returningUnknownLoan() throws Exception {
        returnLoan(newUserId(), 999_999).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- my loans

    @Test
    void myLoansListsOnlyMyActiveLoansSoonestDueFirst() throws Exception {
        long me = newUserId();
        long first = borrowOk(me, newBook(1));
        clock.advance(Duration.ofHours(1));
        long second = borrowOk(me, newBook(1));
        long returned = borrowOk(me, newBook(1));
        returnLoan(me, returned).andExpect(status().isOk());
        borrowOk(newUserId(), newBook(1));   // someone else's loan

        mockMvc.perform(get("/api/v1/me/loans").header(USER, me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[1].id").value(second));
    }

    @Test
    void journalIssuesAreLentLikeEbooksAndListedByIssue() throws Exception {
        long me = newUserId();
        long issue = newJournalIssue(1);
        long otherIssueOfSameJournal = newJournalIssue(1);

        borrow(me, issue)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookFormat").value("JOURNAL"))
                .andExpect(jsonPath("$.bookIssue").exists())
                .andExpect(jsonPath("$.bookAuthor").doesNotExist());
        borrowOk(me, otherIssueOfSameJournal);
        borrow(newUserId(), issue)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_UNAVAILABLE"));

        // same journal title twice; the issue tells the two loans apart
        JsonNode items = json(mockMvc.perform(get("/api/v1/me/loans").header(USER, me))).get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("bookTitle").asText()).isEqualTo(items.get(1).get("bookTitle").asText());
        assertThat(items.get(0).get("bookIssue").asText()).isNotEqualTo(items.get(1).get("bookIssue").asText());
    }

    @Test
    void loansPastDueDateAreFlaggedOverdue() throws Exception {
        long me = newUserId();
        borrowOk(me, newBook(1));

        clock.advance(Duration.ofDays(14).plusSeconds(1));

        mockMvc.perform(get("/api/v1/me/loans").header(USER, me))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].overdue").value(true));
    }

    @Test
    void myLoansIsEmptyForNewUser() throws Exception {
        mockMvc.perform(get("/api/v1/me/loans").header(USER, newUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions borrow(long userId, long bookId) throws Exception {
        return mockMvc.perform(post("/api/v1/loans").header(USER, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + "}"));
    }

    private long borrowOk(long userId, long bookId) throws Exception {
        return json(borrow(userId, bookId).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions returnLoan(long userId, long loanId) throws Exception {
        return mockMvc.perform(post("/api/v1/loans/{id}/return", loanId).header(USER, userId));
    }

    private long ownerOf(long loanId) {
        return jdbc.queryForObject("SELECT user_id FROM loan WHERE id = ?", Long.class, loanId);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
