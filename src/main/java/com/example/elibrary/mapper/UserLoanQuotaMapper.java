package com.example.elibrary.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Per-user count of active loans. Counting loans with SELECT COUNT(*) and then inserting would let two
 * concurrent borrows of different books both pass the limit; a conditional UPDATE on this row cannot.
 */
@Mapper
public interface UserLoanQuotaMapper {

    /**
     * Reserves one loan slot if the user is below the limit.
     *
     * @return 1 if reserved, 0 if the limit is reached or the user has no quota row yet
     */
    @Update("UPDATE user_loan_quota SET active_count = active_count + 1"
            + " WHERE user_id = #{userId} AND active_count < #{max}")
    int tryAcquire(@Param("userId") long userId, @Param("max") int max);

    /**
     * Frees one loan slot.
     *
     * @return 1 if freed, 0 if there was nothing to free (an invariant violation)
     */
    @Update("UPDATE user_loan_quota SET active_count = active_count - 1"
            + " WHERE user_id = #{userId} AND active_count > 0")
    int release(@Param("userId") long userId);

    @Select("SELECT EXISTS(SELECT 1 FROM user_loan_quota WHERE user_id = #{userId})")
    boolean exists(@Param("userId") long userId);

    /**
     * @throws org.springframework.dao.DuplicateKeyException if a concurrent request created the row first
     */
    @Insert("INSERT INTO user_loan_quota (user_id, active_count) VALUES (#{userId}, 0)")
    void insert(@Param("userId") long userId);
}
