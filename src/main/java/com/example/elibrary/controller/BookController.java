package com.example.elibrary.controller;

import com.example.elibrary.common.Page;
import com.example.elibrary.dto.BookDetailResponse;
import com.example.elibrary.dto.BookSearchRequest;
import com.example.elibrary.dto.BookSummaryResponse;
import com.example.elibrary.service.BookQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Positive;

/**
 * 馆藏查询接口：浏览书籍、查看书籍详情。
 * <p>
 * 公开接口，不需要用户身份（不需要 X-User-Id 请求头）。"书籍"在本项目中指任何可借阅的条目：
 * 电子书，或期刊的某一期。
 */
@Tag(name = "Books")
@Validated
@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
public class BookController {

    private final BookQueryService bookQueryService;

    /**
     * 浏览书籍（对应需求：瀏覽書籍）。
     * <p>
     * 分页列出<b>在架</b>的电子书和期刊，已下架的不会出现。支持：
     * <ul>
     *   <li>keyword：按书名或作者模糊搜索，不区分大小写，% 和 _ 按普通字符处理；</li>
     *   <li>format：只看电子书（EBOOK）或期刊（JOURNAL）；</li>
     *   <li>category：按分类筛选；</li>
     *   <li>sort：按书名（TITLE，默认）或最新上架（NEWEST）排序；</li>
     *   <li>page / size：分页，size 最大 100，page × size 最大 10,000。</li>
     * </ul>
     * 返回：200，分页结果（含总条数、总页数）。列表不含简介，简介请调详情接口。
     * <br>错误：400 INVALID_REQUEST（参数不合法，如不支持的排序方式、翻页过深）。
     *
     * 示例：{@code GET /api/v1/books?keyword=java&format=EBOOK&page=1&size=20}
     */
    @Operation(summary = "Browse e-books and journal issues on the shelf, with optional keyword / format / category filters")
    @GetMapping
    public Page<BookSummaryResponse> search(@Valid @ParameterObject BookSearchRequest request) {
        return bookQueryService.search(request.getKeyword(), request.getFormat(), request.getCategory(),
                        request.getSort(), request.getPage(), request.getSize())
                .map(BookSummaryResponse::from);
    }

    /**
     * 查询书籍详细资料（对应需求：查詢書籍詳細資料）。
     * <p>
     * 返回一个条目的完整信息，包括简介、授权份数和当前可借份数。字段按类型不同：
     * 电子书返回 isbn、author；期刊返回 issn、issue（刊期）、publishedOn（出版日期）。
     * 已下架的书仍可查看详情（已借到的读者需要知道自己借的是什么），此时 available 为 false。
     * <p>
     * 返回：200，书籍详情。
     * <br>错误：404 BOOK_NOT_FOUND（书不存在）；400 INVALID_REQUEST（ID 不是正整数）。
     *
     * 示例：{@code GET /api/v1/books/13}
     */
    @Operation(summary = "Book details")
    @GetMapping("/{bookId}")
    public BookDetailResponse get(@PathVariable @Positive long bookId) {
        return BookDetailResponse.from(bookQueryService.getBook(bookId));
    }
}
