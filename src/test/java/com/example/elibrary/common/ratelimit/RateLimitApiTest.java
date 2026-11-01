package com.example.elibrary.common.ratelimit;

import com.example.elibrary.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "elibrary.rate-limit.enabled=true",
        "elibrary.rate-limit.capacity=2",
        "elibrary.rate-limit.refill-per-second=0.01"
})
class RateLimitApiTest extends IntegrationTest {

    @Test
    void writesBeyondTheBurstAreRejectedPerUser() throws Exception {
        long user = newUserId();
        long unknownBook = 999_999;

        borrow(user, unknownBook).andExpect(status().isNotFound());
        borrow(user, unknownBook).andExpect(status().isNotFound());
        borrow(user, unknownBook)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "100"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));

        // another user has their own bucket
        borrow(newUserId(), unknownBook).andExpect(status().isNotFound());
    }

    @Test
    void readsAreNotLimited() throws Exception {
        long user = newUserId();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/me/loans").header("X-User-Id", user)).andExpect(status().isOk());
        }
    }

    private ResultActions borrow(long userId, long bookId) throws Exception {
        return mockMvc.perform(post("/api/v1/loans").header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + "}"));
    }
}
