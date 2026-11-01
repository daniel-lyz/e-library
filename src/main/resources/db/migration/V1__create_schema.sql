-- =====================================================================================================
-- 电子图书馆表结构。兼容 H2（MySQL 模式）和 MySQL 8。
--
-- 表
--   book             馆藏：每一个可借阅的条目（一本电子书，或期刊的某一期）
--   loan             借阅记录：用户每借一次就是一行；永不删除，因此同时也是借阅历史
--   user_loan_quota  借阅配额：每个用户一行，记录当前借着几本（用于执行"每人最多借 N 本"）
--
-- 表之间的关系
--   book 1 ---- * loan        （loan.book_id -> book.id）
--   loan.user_id = user_loan_quota.user_id
--   没有用户表：用户由外部系统管理，这里只保存用户 ID。
--
-- 不变量（由借书 / 还书事务维护，并由下面的约束兜底）
--   book.total_copies            = book.available_copies + 该书借阅中（ACTIVE）的借阅数量
--   user_loan_quota.active_count = 该用户借阅中（ACTIVE）的借阅数量
--
-- 约定
--   * 状态用可读的字符串，并用 CHECK 约束限定取值，不用数字编码。
--   * 不做逻辑删除：书通过 status 下架，借阅通过 status 标记已归还。
--   * 时间字段为 DATETIME(3)（精确到毫秒）。程序内部统一使用 UTC；连接 MySQL 时把连接时区固定为 UTC
--     （connectionTimeZone=UTC，见 README），库里存的就是 UTC。默认的内存 H2 按 JVM 时区存储，
--     由于读写都在同一个 JVM 内，不影响正确性。
-- =====================================================================================================


-- -----------------------------------------------------------------------------------------------------
-- book：馆藏，每行是一个可借阅的条目。
--
-- 电子书和期刊共用这张表：两者的借阅规则完全相同，只是标识和描述方式不同。format 决定哪些字段必填：
--   EBOOK   -> 必须有 isbn、author             （issn / issue / published_on 必须为空）
--   JOURNAL -> 必须有 issn、issue、published_on  （isbn 必须为空；author 通常为空）
-- 期刊按"期"借阅："Communications of the ACM 第 67 卷第 3 期"和"第 4 期"是两行。
--
-- available_copies 是冗余计数，只能在借书 / 还书事务里通过条件更新修改，例如：
--   UPDATE book SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0
-- "检查还有没有"和"扣减"在一条语句里原子完成，所以最后一份不可能同时借给两个人。
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE book
(
    id               BIGINT        NOT NULL AUTO_INCREMENT
        COMMENT '主键。即接口中的 bookId。',
    format           VARCHAR(16)   NOT NULL
        COMMENT '内容类型：EBOOK 电子书 / JOURNAL 期刊（期刊的某一期）。决定哪些标识字段必填。',
    title            VARCHAR(255)  NOT NULL
        COMMENT '书名，或期刊名。同一本期刊的各期书名相同。',
    isbn             VARCHAR(20)   NULL
        COMMENT '仅电子书：国际标准书号。EBOOK 必填，JOURNAL 必须为空。唯一。',
    author           VARCHAR(255)  NULL
        COMMENT '仅电子书：作者。EBOOK 必填；期刊没有单一作者，为空。',
    issn             VARCHAR(9)    NULL
        COMMENT '仅期刊：国际标准连续出版物号（格式 NNNN-NNNN），标识"哪本期刊"。JOURNAL 必填。',
    issue            VARCHAR(64)   NULL
        COMMENT '仅期刊：刊期，标识"哪一期"，如 "Vol. 67, No. 3"。各期刊写法不统一，所以用文本。与 issn 组合唯一。',
    published_on     DATE          NULL
        COMMENT '仅期刊：该期的出版日期。JOURNAL 必填。',
    category         VARCHAR(64)   NOT NULL
        COMMENT '分类，如 Software、Fiction、Computing。浏览时可按它筛选。',
    description      VARCHAR(2000) NOT NULL DEFAULT ''
        COMMENT '简介。只在详情接口返回，浏览列表不返回。',
    total_copies     INT           NOT NULL
        COMMENT '授权份数：最多能同时借给几个人。',
    available_copies INT           NOT NULL
        COMMENT '当前可借份数（未被借出的授权数）。只能在借书 / 还书时通过条件更新修改，取值始终在 0 到 total_copies 之间。',
    status           VARCHAR(16)   NOT NULL
        COMMENT '上架状态。ON_SHELF：在架，可借。OFF_SHELF：已下架，不出现在浏览列表、不能新借，已借出的不受影响。',
    created_at       DATETIME(3)   NOT NULL
        COMMENT '上架时间。"最新上架"排序（sort=NEWEST）依据它。',
    updated_at       DATETIME(3)   NOT NULL
        COMMENT '最后修改时间，每次借书 / 还书修改可借份数时同步更新。',
    PRIMARY KEY (id),
    -- 同一本电子书不能录入两次（期刊的 isbn 为空，唯一索引允许多个空值，不会冲突）
    CONSTRAINT uk_book_isbn UNIQUE (isbn),
    -- 同一本期刊的同一期不能录入两次
    CONSTRAINT uk_book_journal_issue UNIQUE (issn, issue),
    -- 每种类型恰好带自己的标识字段
    CONSTRAINT ck_book_format CHECK (
        (format = 'EBOOK' AND isbn IS NOT NULL AND author IS NOT NULL
            AND issn IS NULL AND issue IS NULL AND published_on IS NULL)
        OR
        (format = 'JOURNAL' AND issn IS NOT NULL AND issue IS NOT NULL AND published_on IS NOT NULL
            AND isbn IS NULL)
    ),
    -- 最后一道防线：可借份数不会变成负数，也不会超过授权份数
    CONSTRAINT ck_book_copies CHECK (available_copies >= 0 AND available_copies <= total_copies),
    CONSTRAINT ck_book_status CHECK (status IN ('ON_SHELF', 'OFF_SHELF'))
);

-- 浏览（默认排序）：在架的书按书名排序（主键隐式作为第二排序条件）
CREATE INDEX idx_book_status_title ON book (status, title);
-- 浏览（sort=NEWEST）：在架的书按上架时间排序
CREATE INDEX idx_book_status_created ON book (status, created_at);


-- -----------------------------------------------------------------------------------------------------
-- loan：借阅记录，用户每借一次就是一行。
--
-- 借书时插入一行（status = ACTIVE，active_flag = 1）；还书时更新这一行（status = RETURNED，
-- 写入 returned_at，active_flag 置为 NULL）。记录永不删除，因此这张表同时也是借阅历史。
--
-- "逾期"不存储：读取时实时计算（status = ACTIVE 且 当前时间 > due_at），
-- 因此永远准确，也不需要定时任务去改状态。
--
-- 为什么需要 active_flag：规则是"同一个人同一本书同时只能借一次，还了可以再借，并且保留历史"。
-- 唯一索引 uk_loan_active (user_id, book_id, active_flag) 正好实现了这个规则：
--   * 借阅中的记录 active_flag = 1，所以同一用户、同一条目只能有一条借阅中的记录；
--   * 已归还的记录 active_flag = NULL，而唯一索引允许任意多个 NULL，所以历史借阅互不冲突。
-- 即使两个相同的借书请求同时到达，也由数据库保证只有一个能插入成功。
-- 示例（用户 1001，条目 1）：id 1 RETURNED active_flag NULL  |  id 2 ACTIVE active_flag 1  -> 两行可以共存
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE loan
(
    id          BIGINT      NOT NULL AUTO_INCREMENT
        COMMENT '主键。即接口中的 loanId；借书响应的 Location 头指向它。',
    user_id     BIGINT      NOT NULL
        COMMENT '借书人，来自请求头 X-User-Id。所有查询和更新都带上它，用于归属校验（防止操作别人的借阅）。',
    book_id     BIGINT      NOT NULL
        COMMENT '借的是哪个条目，引用 book.id。',
    status      VARCHAR(16) NOT NULL
        COMMENT '借阅状态。ACTIVE：借阅中。RETURNED：已归还。逾期在读取时计算，不存储。',
    borrowed_at DATETIME(3) NOT NULL
        COMMENT '借出时间。',
    due_at      DATETIME(3) NOT NULL
        COMMENT '到期时间 = 借出时间 + 借期（默认 14 天）。借书时就算好存下，以后修改借期配置不影响已借出的书。',
    returned_at DATETIME(3) NULL
        COMMENT '归还时间。借阅中为空。',
    active_flag TINYINT     NULL
        COMMENT '借阅中为 1，归还后为 NULL。唯一用途是配合唯一索引 uk_loan_active：同一用户同一条目最多一条借阅中的记录，历史记录不限条数。',
    PRIMARY KEY (id),
    -- 同一用户、同一条目最多只有一条借阅中的记录（原理见上方说明）
    CONSTRAINT uk_loan_active UNIQUE (user_id, book_id, active_flag),
    -- 借阅记录不可能指向不存在的条目
    CONSTRAINT fk_loan_book FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT ck_loan_status CHECK (status IN ('ACTIVE', 'RETURNED'))
);

-- "我的借阅"：查询某个用户借阅中的记录
CREATE INDEX idx_loan_user_status ON loan (user_id, status);
-- 预留给将来的逾期扫描 / 自动归还：查找已过到期时间的借阅中记录
CREATE INDEX idx_loan_status_due ON loan (status, due_at);


-- -----------------------------------------------------------------------------------------------------
-- user_loan_quota：借阅配额，每个用户一行，记录他当前借着几本。
--
-- 用于执行"每人最多同时借 N 本"（默认 N = 5）。如果用 SELECT COUNT(*) 数借阅记录再决定能否借，
-- 并发时会出错：用户已借 4 本，同时借两本"不同"的书，两个请求都数出 4、都判断可以借，结果借了 6 本。
-- 因此借书时用一条语句原子地占用名额：
--   UPDATE user_loan_quota SET active_count = active_count + 1 WHERE user_id = ? AND active_count < 5
-- （影响 0 行 = 已达上限）；还书时用 active_count - 1 释放名额。
--
-- 用户第一次借书时创建这一行。由于每次借书和还书都先更新这一行，同一用户的写操作会在这里排队，
-- 加锁顺序因此固定，避免死锁。
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE user_loan_quota
(
    user_id      BIGINT NOT NULL
        COMMENT '主键：用户 ID（与 loan.user_id 相同）。用户第一次借书时创建。',
    active_count INT    NOT NULL DEFAULT 0
        COMMENT '该用户当前借阅中的数量。借书 +1、还书 -1，都通过条件更新完成。',
    PRIMARY KEY (user_id),
    CONSTRAINT ck_quota_count CHECK (active_count >= 0)
);
