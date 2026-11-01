package com.example.elibrary.controller;

import com.example.elibrary.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses the seeded catalogue (V2__seed_books.sql); never modifies it.
 */
class BookApiTest extends IntegrationTest {

    @Test
    void browsingIsPagedAndExcludesOffShelfBooks() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("category", "Software").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                // 6 software books on the shelf; "Head First Design Patterns" is off the shelf
                .andExpect(jsonPath("$.totalItems").value(6))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/v1/books").param("category", "Software").param("size", "100"))
                .andExpect(jsonPath("$.items[*].title", not(hasItem("Head First Design Patterns"))));
    }

    @Test
    void browsingCanBeFilteredByFormat() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("format", "JOURNAL").param("category", "Computing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].format", everyItem(equalTo("JOURNAL"))))
                .andExpect(jsonPath("$.items[*].issue", contains("Vol. 67, No. 3", "Vol. 67, No. 4")))
                .andExpect(jsonPath("$.items[0].author").doesNotExist());

        mockMvc.perform(get("/api/v1/books").param("format", "EBOOK").param("category", "Software"))
                .andExpect(jsonPath("$.totalItems").value(6))
                .andExpect(jsonPath("$.items[0].issue").doesNotExist());

        mockMvc.perform(get("/api/v1/books").param("format", "MAGAZINE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("format"));
    }

    @Test
    void journalIssueDetailsCarryIssnIssueAndDateInsteadOfIsbnAndAuthor() throws Exception {
        mockMvc.perform(get("/api/v1/books/{id}", 13))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("JOURNAL"))
                .andExpect(jsonPath("$.title").value("Communications of the ACM"))
                .andExpect(jsonPath("$.issn").value("0001-0782"))
                .andExpect(jsonPath("$.issue").value("Vol. 67, No. 3"))
                .andExpect(jsonPath("$.publishedOn").value("2024-03-01"))
                .andExpect(jsonPath("$.isbn").doesNotExist())
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void schemaRejectsFieldsThatDoNotFitTheFormat() {
        // a journal issue must say which issue it is
        assertThatThrownBy(() -> jdbc.update("INSERT INTO book (format, issn, published_on, title, category,"
                + " total_copies, available_copies, status, created_at, updated_at) VALUES ('JOURNAL', '1234-5678',"
                + " DATE '2024-01-01', 'X', 'Test', 1, 1, 'ON_SHELF', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // an e-book has no ISSN
        assertThatThrownBy(() -> jdbc.update("INSERT INTO book (format, isbn, author, issn, title, category,"
                + " total_copies, available_copies, status, created_at, updated_at) VALUES ('EBOOK', 'X-1', 'A',"
                + " '1234-5678', 'X', 'Test', 1, 1, 'ON_SHELF', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void defaultSortIsByTitle() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("category", "Fiction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("1984"))
                .andExpect(jsonPath("$.items[1].title").value("Pride and Prejudice"))
                .andExpect(jsonPath("$.items[2].title").value("To Kill a Mockingbird"));
    }

    @Test
    void keywordMatchesTitleOrAuthorCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("keyword", "ORWELL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("1984"));

        mockMvc.perform(get("/api/v1/books").param("keyword", "java"))
                .andExpect(jsonPath("$.items[*].title", everyItem(org.hamcrest.Matchers.containsStringIgnoringCase("java"))));
    }

    @Test
    void likeWildcardsInKeywordAreTreatedLiterally() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void invalidQueryParametersAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("sort", "price"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.violations[0].field").value("sort"));

        mockMvc.perform(get("/api/v1/books").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("size"));

        mockMvc.perform(get("/api/v1/books").param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagingDepthIsCappedAtTheResultWindow() throws Exception {
        // exactly at the window (100 * 100 = 10,000): allowed, just an empty page
        mockMvc.perform(get("/api/v1/books").param("page", "100").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/books").param("page", "10000").param("size", "1"))
                .andExpect(status().isOk());

        // one page further: rejected before any SQL runs
        mockMvc.perform(get("/api/v1/books").param("page", "101").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("withinResultWindow"));
        mockMvc.perform(get("/api/v1/books").param("page", "10001").param("size", "1"))
                .andExpect(status().isBadRequest());

        // no int overflow on huge page numbers
        mockMvc.perform(get("/api/v1/books").param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookDetails() throws Exception {
        mockMvc.perform(get("/api/v1/books/{id}", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Domain-Driven Design"))
                .andExpect(jsonPath("$.isbn").value("9780321125217"))
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.status").value("ON_SHELF"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void offShelfBookDetailsAreStillVisibleForExistingReaders() throws Exception {
        mockMvc.perform(get("/api/v1/books/{id}", 12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void unknownOrMalformedBookId() throws Exception {
        mockMvc.perform(get("/api/v1/books/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/v1/books/abc"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/books/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEndpointReturnsJson404() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
