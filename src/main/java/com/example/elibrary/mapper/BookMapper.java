package com.example.elibrary.mapper;

import com.example.elibrary.domain.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SQL lives in resources/mapper/BookMapper.xml.
 */
@Mapper
public interface BookMapper {

    Optional<Book> findById(@Param("id") long id);

    List<Book> search(BookSearchCriteria criteria);

    long count(BookSearchCriteria criteria);

    /**
     * Takes one copy if, and only if, one is left and the book is on the shelf.
     * Check and decrement happen in one statement, so concurrent borrowers cannot both take the last copy.
     *
     * @return 1 when a copy was taken, 0 otherwise
     */
    int decrementAvailable(@Param("id") long id, @Param("now") Instant now);

    /**
     * Puts one copy back, never above the licence count.
     *
     * @return 1 when a copy was put back, 0 if the counter was already full (an invariant violation)
     */
    int incrementAvailable(@Param("id") long id, @Param("now") Instant now);
}
