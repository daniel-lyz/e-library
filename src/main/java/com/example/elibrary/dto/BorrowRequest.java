package com.example.elibrary.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Getter
@Setter
public class BorrowRequest {

    @NotNull
    @Positive
    private Long bookId;
}
