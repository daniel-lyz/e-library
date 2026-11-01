package com.example.elibrary.mapper;

import com.example.elibrary.domain.Loan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SQL lives in resources/mapper/LoanMapper.xml.
 */
@Mapper
public interface LoanMapper {

    /**
     * Inserts an ACTIVE loan and sets its generated id.
     *
     * @throws org.springframework.dao.DuplicateKeyException if the user already has this book on loan
     */
    void insert(Loan loan);

    boolean existsActive(@Param("userId") long userId, @Param("bookId") long bookId);

    /**
     * Closes the loan only if it belongs to the user and is still active, so a repeated or forged
     * return changes nothing.
     *
     * @return 1 if the loan was closed, 0 otherwise
     */
    int markReturned(@Param("loanId") long loanId, @Param("userId") long userId, @Param("now") Instant now);

    Optional<Loan> findByIdAndUser(@Param("loanId") long loanId, @Param("userId") long userId);

    Optional<LoanDetails> findDetails(@Param("loanId") long loanId, @Param("userId") long userId);

    List<LoanDetails> findActiveDetails(@Param("userId") long userId);
}
