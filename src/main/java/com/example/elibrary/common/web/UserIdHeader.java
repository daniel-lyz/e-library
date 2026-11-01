package com.example.elibrary.common.web;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Reads the caller identity set by the gateway.
 */
public final class UserIdHeader {

    public static final String NAME = "X-User-Id";

    private UserIdHeader() {
    }

    /**
     * @return the user id, or empty when the header is missing or not a positive number
     */
    public static Optional<Long> read(HttpServletRequest request) {
        String raw = request.getHeader(NAME);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(raw.trim());
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
