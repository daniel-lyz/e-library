package com.example.elibrary.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookQueryServiceTest {

    @Test
    void blankKeywordMeansNoFilter() {
        assertThat(BookQueryService.toLikePattern(null)).isNull();
        assertThat(BookQueryService.toLikePattern("   ")).isNull();
    }

    @Test
    void keywordIsTrimmedLowerCasedAndWrapped() {
        assertThat(BookQueryService.toLikePattern("  Java ")).isEqualTo("%java%");
    }

    @Test
    void likeWildcardsAndEscapeCharAreEscaped() {
        assertThat(BookQueryService.toLikePattern("100%_off!")).isEqualTo("%100!%!_off!!%");
    }
}
