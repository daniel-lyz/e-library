package com.example.elibrary.controller;

import com.example.elibrary.common.web.CurrentUserId;
import com.example.elibrary.dto.BorrowRequest;
import com.example.elibrary.dto.LoanListResponse;
import com.example.elibrary.dto.LoanResponse;
import com.example.elibrary.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.net.URI;
import java.util.stream.Collectors;

/**
 * 借阅接口：借书、还书、查看我的借阅。
 * <p>
 * 借阅（loan）是一个独立的资源：借书时创建一条借阅记录，还书时把它标记为"已归还"，记录永不删除。
 * <p>
 * 所有接口都需要用户身份：从请求头 X-User-Id 获取（由 {@link CurrentUserId} 注入），缺失或不合法返回
 * 401 UNAUTHENTICATED。用户 ID 只从请求头获取，不从 URL 或请求体获取，因此无法冒充他人。
 * 借书和还书按用户限流，超出返回 429 TOO_MANY_REQUESTS。
 */
@Tag(name = "Loans")
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    /**
     * 借阅书籍（对应需求：借閱書籍）。
     * <p>
     * 当前用户借一本电子书或一期期刊。成功后可借份数减 1，生成一条"借阅中"的记录，到期时间 = 现在 + 借期（默认 14 天）。
     * 多人同时借最后一份时，只有一人能成功（由数据库条件更新保证，见 LoanService）。
     * <p>
     * 返回：201 Created，借阅记录；响应头 Location 指向这条借阅（GET /api/v1/loans/{id}）。
     * <br>错误：
     * <ul>
     *   <li>404 BOOK_NOT_FOUND：书不存在；</li>
     *   <li>409 BOOK_UNAVAILABLE：已被借完；</li>
     *   <li>409 ALREADY_BORROWED：你已经借了这本，还没还；</li>
     *   <li>409 LOAN_LIMIT_EXCEEDED：已达借阅上限（默认 5 本）；</li>
     *   <li>422 BOOK_NOT_BORROWABLE：书已下架；</li>
     *   <li>400 INVALID_REQUEST：请求体缺少 bookId 或格式错误。</li>
     * </ul>
     *
     * 示例：{@code POST /api/v1/loans}，请求头 {@code X-User-Id: 7}，请求体 {@code {"bookId": 1}}
     */
    @Operation(summary = "Borrow a book")
    @PostMapping("/loans")
    public ResponseEntity<LoanResponse> borrow(@CurrentUserId long userId, @Valid @RequestBody BorrowRequest request) {
        LoanResponse loan = LoanResponse.from(loanService.borrow(userId, request.getBookId()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(loan.getId()).toUri();
        return ResponseEntity.created(location).body(loan);
    }

    /**
     * 归还书籍（对应需求：歸還書籍）。
     * <p>
     * 当前用户归还自己的一条借阅。借阅记录变为"已归还"并记录归还时间（记录保留，不删除），
     * 可借份数加 1，用户的借阅名额释放 1 个。还了之后可以再借同一本。
     * 用 POST 而不是 DELETE：归还是状态变更，不是删除记录。
     * <p>
     * 返回：200，更新后的借阅记录（status = RETURNED）。
     * <br>错误：
     * <ul>
     *   <li>404 LOAN_NOT_FOUND：借阅不存在，或不属于你（返回 404 而不是 403，不泄露该借阅是否存在）；</li>
     *   <li>409 LOAN_ALREADY_RETURNED：已经归还过（重复请求不会把份数加两次）。</li>
     * </ul>
     *
     * 示例：{@code POST /api/v1/loans/1/return}，请求头 {@code X-User-Id: 7}
     */
    @Operation(summary = "Return a borrowed book")
    @PostMapping("/loans/{loanId}/return")
    public LoanResponse returnBook(@CurrentUserId long userId, @PathVariable @Positive long loanId) {
        return LoanResponse.from(loanService.returnLoan(userId, loanId));
    }

    /**
     * 查看我的一条借阅（支撑接口，需求未单独列出）。
     * <p>
     * 按 ID 查看当前用户的一条借阅，借阅中或已归还都可以查。借书成功返回的 Location 头指向这个接口，
     * 按 REST 约定，新创建资源的地址应当可以访问。
     * <p>
     * 返回：200，借阅记录（含书名、到期时间、是否逾期）。
     * <br>错误：404 LOAN_NOT_FOUND（借阅不存在，或不属于你）。
     *
     * 示例：{@code GET /api/v1/loans/1}，请求头 {@code X-User-Id: 7}
     */
    @Operation(summary = "One of my loans (active or returned)")
    @GetMapping("/loans/{loanId}")
    public LoanResponse get(@CurrentUserId long userId, @PathVariable @Positive long loanId) {
        return LoanResponse.from(loanService.getLoan(userId, loanId));
    }

    /**
     * 查看目前已借阅的书籍（对应需求：查看目前已借閱的書籍）。
     * <p>
     * 列出当前用户所有"借阅中"的书，最快到期的排在最前面；已归还的不在列表中。
     * 每条带书名、作者（电子书）或刊期（期刊）、到期时间，以及是否逾期（overdue，按当前时间实时计算）。
     * 数量受借阅上限约束（默认最多 5 条），所以不分页。
     * 路径用 /me：只能查自己的借阅，URL 里没有用户 ID，无法通过改 URL 查看别人的记录。
     * <p>
     * 返回：200，{@code {"items": [...]}}；没有借阅时 items 为空数组。
     *
     * 示例：{@code GET /api/v1/me/loans}，请求头 {@code X-User-Id: 7}
     */
    @Operation(summary = "Books I currently have on loan, soonest due first")
    @GetMapping("/me/loans")
    public LoanListResponse myActiveLoans(@CurrentUserId long userId) {
        return new LoanListResponse(loanService.activeLoans(userId).stream()
                .map(LoanResponse::from)
                .collect(Collectors.toList()));
    }
}
