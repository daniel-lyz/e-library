package com.example.elibrary.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTest {

    @Test
    void onShelfBookIsBorrowable() {
        Book book = Book.builder().id(1L).status(BookStatus.ON_SHELF).totalCopies(2).availableCopies(1).build();

        assertThatCode(book::assertBorrowable).doesNotThrowAnyException();
        assertThat(book.hasAvailableCopy()).isTrue();
    }

    @Test
    void offShelfBookIsNotBorrowableEvenWithCopiesLeft() {
        Book book = Book.builder().id(1L).status(BookStatus.OFF_SHELF).totalCopies(2).availableCopies(2).build();

        assertThatThrownBy(book::assertBorrowable).isInstanceOf(BookNotBorrowableException.class);
        assertThat(book.hasAvailableCopy()).isFalse();
    }

    @Test
    void noCopyLeftIsReportedButStillBorrowableByRule() {
        // whether a copy is left is decided atomically by the database at borrow time, not by this snapshot
        Book book = Book.builder().id(1L).status(BookStatus.ON_SHELF).totalCopies(1).availableCopies(0).build();

        assertThat(book.hasAvailableCopy()).isFalse();
        assertThatCode(book::assertBorrowable).doesNotThrowAnyException();
    }
}
