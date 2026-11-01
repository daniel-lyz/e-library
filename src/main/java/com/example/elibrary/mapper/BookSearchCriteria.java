package com.example.elibrary.mapper;

import com.example.elibrary.domain.BookFormat;
import com.example.elibrary.domain.BookSort;
import lombok.Value;

/**
 * Query parameters as the SQL needs them: keyword already escaped and wrapped in wildcards.
 */
@Value
public class BookSearchCriteria {

    /** lower-cased LIKE pattern using '!' as escape character, or null */
    String keywordPattern;
    /** null means all formats */
    BookFormat format;
    String category;
    BookSort sort;
    int offset;
    int limit;
}
