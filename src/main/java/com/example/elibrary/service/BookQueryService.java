package com.example.elibrary.service;

import com.example.elibrary.common.Page;
import com.example.elibrary.domain.Book;
import com.example.elibrary.domain.BookFormat;
import com.example.elibrary.domain.BookNotFoundException;
import com.example.elibrary.domain.BookSort;
import com.example.elibrary.mapper.BookMapper;
import com.example.elibrary.mapper.BookSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BookQueryService {

    private final BookMapper bookMapper;

    public Page<Book> search(String keyword, BookFormat format, String category, BookSort sort, int page, int size) {
        BookSearchCriteria criteria = new BookSearchCriteria(
                toLikePattern(keyword), format, blankToNull(category), sort, (page - 1) * size, size);

        long total = bookMapper.count(criteria);
        List<Book> items = total == 0 ? Collections.emptyList() : bookMapper.search(criteria);
        return Page.of(items, page, size, total);
    }

    public Book getBook(long bookId) {
        return bookMapper.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
    }

    /**
     * Case-insensitive "contains" match. LIKE wildcards typed by the user are escaped, so searching
     * for "100%" means the literal text. '!' is used as the escape character because it is written the
     * same way in H2 and MySQL string literals (a backslash is not).
     */
    static String toLikePattern(String keyword) {
        String trimmed = blankToNull(keyword);
        if (trimmed == null) {
            return null;
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
