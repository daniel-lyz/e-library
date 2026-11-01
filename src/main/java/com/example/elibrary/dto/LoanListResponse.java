package com.example.elibrary.dto;

import lombok.Value;

import java.util.List;

/**
 * Wrapped in an object rather than a bare array, so fields such as paging can be added later
 * without breaking clients.
 */
@Value
public class LoanListResponse {

    List<LoanResponse> items;
}
