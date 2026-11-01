package com.example.elibrary.support;

import com.example.elibrary.domain.BookStatus;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Full application on the in-memory H2 database. Tests create their own books and users through the
 * helpers below, so they never depend on each other's data and need no cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(1_000_000);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TestClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    protected static long newUserId() {
        return IDS.incrementAndGet();
    }

    protected long newBook(int copies) {
        return newBook(copies, BookStatus.ON_SHELF);
    }

    protected long newBook(int copies, BookStatus status) {
        long n = IDS.incrementAndGet();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO book (format, isbn, title, author, category, description, total_copies,"
                            + " available_copies, status, created_at, updated_at)"
                            + " VALUES ('EBOOK', ?, ?, 'Test Author', 'Test', '', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    new String[]{"id"});
            ps.setString(1, "T" + n);
            ps.setString(2, "Test Book " + n);
            ps.setInt(3, copies);
            ps.setInt(4, copies);
            ps.setString(5, status.name());
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    /** One issue of a test journal; each call creates a new issue of the same journal. */
    protected long newJournalIssue(int copies) {
        long n = IDS.incrementAndGet();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO book (format, issn, issue, published_on, title, category, description, total_copies,"
                            + " available_copies, status, created_at, updated_at)"
                            + " VALUES ('JOURNAL', '9999-9999', ?, DATE '2024-01-01', 'Test Journal', 'Test', '', ?, ?,"
                            + " 'ON_SHELF', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    new String[]{"id"});
            ps.setString(1, "No. " + n);
            ps.setInt(2, copies);
            ps.setInt(3, copies);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    protected int availableCopies(long bookId) {
        return jdbc.queryForObject("SELECT available_copies FROM book WHERE id = ?", Integer.class, bookId);
    }

    protected int activeLoansOfBook(long bookId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE book_id = ? AND status = 'ACTIVE'",
                Integer.class, bookId);
    }

    protected int activeLoansOfUser(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE user_id = ? AND status = 'ACTIVE'",
                Integer.class, userId);
    }

    protected int quotaOf(long userId) {
        return jdbc.queryForList("SELECT active_count FROM user_loan_quota WHERE user_id = ?", Integer.class, userId)
                .stream().findFirst().orElse(0);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }
}
