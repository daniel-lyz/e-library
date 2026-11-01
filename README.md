# 电子图书馆服务（E-Library Service）

一个电子图书馆的后端服务，馆藏为数字内容：**电子书和期刊（按期）**。读者可以浏览馆藏、查看条目详情、借阅和归还，以及查看自己当前借着的书。

Java 11 · Spring Boot 2.7 · MyBatis · Flyway · 内存数据库 H2（可通过配置切换到 MySQL 8）

---

## 0. 范围说明

**核心部分（题目要求的内容）：**五个功能、领域模型、API 设计、错误处理和测试。其中包括并发安全，因为没有它，"借书"在并发下就是错误的：最后一份可能被同时借给两个人。

**有意保留的小功能（每个不超过约 50 行）：**

- 借书 / 还书按用户限流；
- 每个请求、每行日志、每个错误都带 `traceId`；
- 借阅事件，用于记录日志和指标。

每一项都是为了说明一个设计点（保护写操作、可运维性、为将来的集成预留扩展点），取舍理由见第 8 节。

**刻意不做的内容：**登录鉴权、在线阅读 / 内容分发、馆藏管理、缓存、消息队列、部署文件。第 8、9 节说明了为什么不做，以及什么时候该做。

---

## 1. 快速开始

```bash
mvn spring-boot:run            # 需要 JDK 11+。无需数据库或其他配置：使用内存 H2，自带演示数据
mvn verify                     # 运行全部测试，包括并发测试
```

- Swagger 接口文档：http://localhost:8080/swagger-ui.html
- 健康检查：http://localhost:8080/actuator/health
- 查看内存数据库中的数据：启动时加参数 `--spring.h2.console.enabled=true`，然后打开 http://localhost:8080/h2-console（JDBC URL 填 `jdbc:h2:mem:elibrary`，用户名 `sa`）。默认关闭，因为控制台可以连接任意 JDBC 地址，存在安全风险。

如需连接 MySQL 8，通过 Spring 的标准配置项指定数据源即可，表结构由同一套 Flyway 脚本自动创建：

```bash
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/elibrary?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
SPRING_DATASOURCE_USERNAME=elibrary SPRING_DATASOURCE_PASSWORD=... mvn spring-boot:run
```

### 试一试

```bash
curl 'localhost:8080/api/v1/books?keyword=java&size=5'
curl 'localhost:8080/api/v1/books?format=JOURNAL'
curl  localhost:8080/api/v1/books/13
curl -X POST localhost:8080/api/v1/loans -H 'X-User-Id: 7' -H 'Content-Type: application/json' -d '{"bookId":1}'
curl  localhost:8080/api/v1/me/loans -H 'X-User-Id: 7'
curl -X POST localhost:8080/api/v1/loans/1/return -H 'X-User-Id: 7'
```

演示数据：

- 1–12 号是电子书，13–15 号是期刊；
- 1 号书只有 1 份授权，第二个人再借会收到 `409 BOOK_UNAVAILABLE`；
- 12 号书已下架。

---

## 2. 接口

| 功能 | 请求 | 成功 | 错误 |
|---|---|---|---|
| 浏览书籍 | `GET /api/v1/books?keyword=&format=EBOOK\|JOURNAL&category=&sort=TITLE\|NEWEST&page=1&size=20`（`size` ≤ 100，`page × size` ≤ 10,000） | 200，分页结果 | 400 |
| 书籍详情 | `GET /api/v1/books/{bookId}` | 200 | 404 `BOOK_NOT_FOUND` |
| 借书 | `POST /api/v1/loans`，请求体 `{"bookId":1}` | 201 + `Location` 头 | 404 `BOOK_NOT_FOUND`；409 `BOOK_UNAVAILABLE` / `ALREADY_BORROWED` / `LOAN_LIMIT_EXCEEDED`；422 `BOOK_NOT_BORROWABLE` |
| 还书 | `POST /api/v1/loans/{loanId}/return` | 200 | 404 `LOAN_NOT_FOUND`；409 `LOAN_ALREADY_RETURNED` |
| 查看我的一条借阅 | `GET /api/v1/loans/{loanId}` | 200 | 404 `LOAN_NOT_FOUND` |
| 我当前的借阅 | `GET /api/v1/me/loans` | 200，最快到期的排在最前 | |

借阅相关接口需要请求头 `X-User-Id`，缺失时返回 `401 UNAUTHENTICATED`。借书和还书按用户限流，超出返回 `429 TOO_MANY_REQUESTS`，并带 `Retry-After` 头。

所有错误都使用同一种响应结构，所有响应都带 `X-Trace-Id` 头：

```json
{ "code": "BOOK_UNAVAILABLE", "message": "no copies of book 1 are available right now",
  "traceId": "9f2c4e...", "timestamp": "2026-11-01T10:00:00Z" }
```

参数校验失败时额外返回 `violations` 列表，例如 `[{"field":"size","message":"must be less than or equal to 100"}]`。接口返回的提示信息固定为英文，不随服务器操作系统的语言变化。

**API 设计决策**

- **借阅是一个资源。**借书就是创建一条借阅（`POST /loans`）；还书是修改它的状态，而不是删除它，所以借阅历史得以保留。
- **用户身份从不来自 URL 或请求体。**`/me/loans` 和所有归属校验都只使用调用者的身份，因此修改请求参数无法看到别人的借阅。
- **HTTP 状态码有明确含义。**网关、监控和客户端的重试逻辑都可以据此处理（429 和 5xx 可以重试，4xx 不应重试）；`code` 字段告诉客户端具体原因。
- **访问别人的借阅返回 404 而不是 403**，这样接口不会泄露哪些借阅 ID 是存在的。

---

## 3. 假设

| 题目没有说明的问题 | 假设 |
|---|---|
| 电子书和期刊有什么不同？ | 期刊**按期**借阅（例如 *Communications of the ACM* 第 67 卷第 3 期）。两种格式的借阅规则完全相同，区别只在标识和描述：电子书有 ISBN 和作者；期刊有 ISSN、刊期和出版日期。接口沿用题目中"书籍"的说法，指代任何可借阅的条目。 |
| 同一个条目能同时借给几个人？ | 每个条目有若干份借阅**授权**（`totalCopies`），一份授权同一时间只借给一位读者。这也是图书馆电子书、电子期刊授权的常见方式。 |
| 借期多久？逾期怎么处理？ | 14 天（可配置）。"逾期"在读取借阅时实时计算；到期不会自动归还（见第 9 节演进路线）。 |
| 每位读者最多借几本？ | 同时最多 5 本（可配置）。 |
| 能重复借同一本吗？ | 上一次借阅还没归还时不能；归还后可以再借。 |
| 用户是谁？ | 登录鉴权不在本题范围内。假设由 API 网关完成鉴权，并通过 `X-User-Id` 请求头传入用户 ID。前提是服务只能经网关访问，且网关会剥离客户端自带的 `X-User-Id`。 |
| 下架的书怎么处理？ | 不出现在浏览列表中，不能新借；已有借阅照常有效，详情仍可查看。 |
| 书籍如何录入？ | 使用 Flyway 种子数据。馆藏管理不在本题范围内。 |
| "阅读"内容怎么做？ | 不在本题范围内，设计思路见第 8 节。 |

---

## 4. 设计

### 架构

单个可部署的服务，**按层分包**：

```
com.example.elibrary
├── controller/  HTTP 接口：转换请求、校验参数
├── dto/         请求体和响应体（API 契约）
├── service/     业务用例和事务（LoanService、BookQueryService）、借阅规则、事件监听
├── domain/      Book、Loan、状态枚举、业务规则和业务异常
├── mapper/      MyBatis Mapper（SQL 在 resources/mapper/*.xml）及查询结果类型
└── common/      错误处理、traceId、调用者身份、限流、配置
```

依赖单向流动：controller → service → mapper，各层都可以使用 domain。domain 不依赖任何其他层，所以它的业务规则不需要 Spring 或数据库就能做单元测试。controller 从不直接返回领域对象，而是转换成 `dto` 类，这样 API 契约和内部模型可以各自独立演进。

- **领域对象**承载业务规则：Book 知道自己能不能借，Loan 知道自己的到期时间以及是否逾期。
- **Mapper** 承载 SQL。使用 MyBatis 是为了让并发相关的关键 SQL 显式可见、便于审查；JPA 会把这些细节隐藏起来。
- Mapper 接口本身就是仓储的边界。在这个规模下，再包一层独立的 Repository 接口只会增加代码，没有收益。

为什么按层分包：服务里只有两个紧密相关的概念（借阅总是涉及书），按功能拆分只会增加目录嵌套，换不来真正的隔离。当馆藏和借阅发展成相互独立的关注点时，自然的下一步是按功能拆分（`book/`、`loan/`），再进一步拆成独立服务。

### 领域模型

- **Book**：馆藏中任何可借阅的条目，带有 `totalCopies`（授权份数）和 `availableCopies`（可借份数）。`format` 为 `EBOOK`（ISBN、作者）或 `JOURNAL`（ISSN、刊期、出版日期）。
- **Loan**：一位用户借一个条目。状态为 `ACTIVE`（借阅中）或 `RETURNED`（已归还），带有 `borrowedAt`、`dueAt`、`returnedAt`。借阅记录永不删除。
- **UserLoanQuota**：每个用户一行，记录其借阅中的数量（见第 5 节）。

**电子书和期刊：一个实体加类型字段，而不是继承体系。**服务对一个条目所做的所有事情——浏览、借阅、归还、上限控制——只取决于它的授权份数和上架状态。类型只影响标识和展示。考虑过三种方案：

| 方案 | 结论 |
|---|---|
| 一张表、一个实体，加 `format` 字段 | **采用。**借阅只有一套代码路径；新增一种类型只需加一个枚举值。`CHECK` 约束保证每种类型恰好带有自己的字段（例如没有刊期的期刊会被数据库直接拒绝）。 |
| 继承（`EBook` / `JournalIssue` 子类，单表或连接表） | 类型和映射代码更多，但两者并没有真正不同的行为。 |
| 每种类型独立建表、独立接口 | 借阅逻辑和并发保证都要重复一遍，而且一条借阅要能指向两种不同的东西。 |

如果将来两种类型的**行为**出现差异（例如期刊只能在线阅读，或者不同类型借期不同），类继承（或多态的规则策略）才值得引入。

**不变量：**`book.total_copies = book.available_copies + 该书借阅中（ACTIVE）的借阅数量`。

`availableCopies` 作为冗余计数存储，而不是每次读取时用 `COUNT(*)` 计算。存储的计数可以用一条条件 `UPDATE` 原子地修改；实时计数则需要加锁，否则"先数再插入"会出现竞态。读取也更快。代价是计数可能与借阅记录不一致，通过四项措施控制：

- 所有修改都在同一个事务中完成；
- `CHECK` 约束保证计数在 0 到 `total_copies` 之间；
- 发现不变量被破坏时记录 ERROR 日志；
- 定期对账任务列入演进路线。

---

## 5. 并发与一致性

正确性只依赖数据库，从不依赖 JVM 锁或缓存，因此无论部署多少个实例都成立：

| 风险 | 防护措施 |
|---|---|
| 两位读者同时借走最后一份 | `UPDATE book SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0`。检查和扣减在一条语句中原子完成；更新 0 行即表示已借完。 |
| 同一位读者并发重复借同一本 | 唯一索引 `(user_id, book_id, active_flag)`。借阅中时 `active_flag` 为 1，归还后为 NULL。唯一索引允许多个 NULL，所以完整的借阅历史得以保留。 |
| 同一位读者并发借多本不同的书，突破上限 | `UPDATE user_loan_quota SET active_count = active_count + 1 WHERE user_id = ? AND active_count < max`。如果"先数再判断"，两个请求都会通过。 |
| 重复归还，或重试还书请求 | `UPDATE loan ... WHERE id = ? AND user_id = ? AND status = 'ACTIVE'`。第二次请求不会改变任何数据；`user_id` 条件同时起到归属校验的作用。 |
| 中途某一步失败 | 每个用例在一个事务中执行，任何一步失败都会回滚之前的所有修改（份数、配额）。 |
| 死锁 | 固定加锁顺序：借书和还书都先更新用户的配额行（借书：配额 → 书 → 借阅；还书：配额 → 借阅 → 书）。同一用户的写操作在第一步就排队，不同用户只在书这一行相遇。如果数据库仍然报告死锁或锁等待超时，映射为 `409 CONCURRENT_CONFLICT`（可安全重试）。 |

**放弃的方案**

- `synchronized` / `ReentrantLock`：只在单个 JVM 内有效。而且在 `@Transactional` 方法中，锁会在事务提交**之前**释放，第二个线程仍可能读到旧数据。
- `SELECT ... FOR UPDATE`：结果正确，但整个事务期间都持有行锁，而且需要"先读后写"。条件更新用一条语句就能做到同样的事。
- 基于 `version` 字段的乐观锁：热门书被争抢时，大部分请求会失败并重试。条件更新不需要重试。
- 分布式锁（如 Redisson）：为了保护一条 SQL 本来就能保证的事情，额外增加一次网络调用和一个故障点。锁的租约还可能在 GC 停顿期间过期，导致两个持有者同时存在。

`LoanConcurrencyTest` 通过让大量线程同时发起请求来证明这些不变量：

- 50 位读者抢最后 1 份：恰好 1 人成功；
- 60 位读者抢 20 份：恰好 20 人成功；
- 一位新读者同时借 12 本：恰好成功 5 本（上限）；
- 同一位读者同时对同一本书发起 10 次借阅：只生成 1 条借阅；
- 同一条借阅同时被归还 10 次：只加回 1 份。

---

## 6. 错误处理、日志与运维

- **错误处理。**业务失败用具体的异常类型表示（如 `BookUnavailableException`），每个异常携带一个 `ErrorCode`，每个错误码对应一个 HTTP 状态码。由一个 `@RestControllerAdvice` 统一把所有异常转换成错误响应。未预期的异常会记录完整堆栈，而客户端只会收到 `500 INTERNAL_ERROR` 和 `traceId`。
- **限流。**借书和还书使用按用户的令牌桶（最多连续 5 次，之后每秒补充 1 次）。令牌桶存放在有容量上限、会过期的 Caffeine 缓存中，内存不会无限增长。读接口不限流。
- **可观测性。**
  - 每个请求都有一个 `traceId`；调用方通过 `X-Trace-Id` 传入的合法值会被沿用。
  - `traceId` 出现在日志、响应头和错误响应体中。
  - 在 `prod` 配置下，日志输出为 JSON 格式。
  - Micrometer 计数器：`elibrary.loans{event}` 和 `elibrary.requests.rejected{code}`。例如 `BOOK_UNAVAILABLE` 突然增多，说明有热门书被争抢。
  - 健康检查地址：`/actuator/health`。
- **时间。**所有时间都是 UTC 的 `Instant`，来自注入的 `Clock`，因此到期和逾期逻辑可以进行确定性的测试。
- **表结构。**H2 和 MySQL 共用同一套 Flyway 迁移脚本。每个字段的含义见 `V1__create_schema.sql` 中的注释。
- **停机。**启用了优雅停机：进程退出前会等待处理中的请求完成。

---

## 7. 测试

| 层级 | 内容 |
|---|---|
| 单元测试 | `Book` 和 `Loan` 的规则（到期时间、逾期的边界），令牌桶（使用模拟时钟），关键词转义 |
| 单元测试：并发时序（`LoanServiceQuotaTest`，使用 mock） | 配额行延迟创建时的每一种竞态结果。压力测试只能碰运气才能命中这些时间窗口，mock 则每次都能精确复现每一种线程交错。这个测试发现过一个真实的 bug：一位读者首次并发借书时，可能在一本书都没借的情况下被提示 `LOAN_LIMIT_EXCEEDED`。 |
| 接口集成测试（`MockMvc` + H2） | 每个接口、每个状态码和错误码；电子书和期刊（按类型筛选、各类型特有的字段、借阅期刊、数据库拒绝不符合类型的字段）；归属校验；借阅上限；分页上限；还书后份数和配额恢复；已下架书籍的借阅；用可调测试时钟验证逾期；限流 |
| 并发测试（真实多线程 + H2） | 上面列出的五个竞态场景 |

测试会自己创建书籍和用户，因此测试之间互不依赖，也不依赖演示数据。

---

## 8. 取舍，以及有意不做的内容

| 决策 | 理由 | 代价 / 何时重新考虑 |
|---|---|---|
| 默认使用 H2 | 评审只需一条命令就能运行全部内容 | H2 和 MySQL 在加锁细节上有差异。并发相关的 SQL 只依赖"单行更新是原子的"和"唯一索引"，这两点在两者上表现一致；但下一步我会用 Testcontainers 在 MySQL 上运行测试。 |
| 不用 Redis，不用消息队列 | 当前没有任何地方需要它们；每引入一个都会带来自己的一致性和故障处理问题 | 事件和限流都已经预留了扩展点（见第 9 节）。 |
| 逾期在读取时计算 | 永远准确，不需要定时任务 | 到期时份数不会自动释放。 |
| 进程内事件（`@TransactionalEventListener(AFTER_COMMIT)`） | 零基础设施成本地解耦附加动作（目前是日志和指标） | 不持久化。任何不能丢失的动作都需要改用 outbox（本地消息表）。 |
| 单实例限流 | 简单，没有额外依赖 | 部署 N 个实例时，一个用户实际能获得 N 倍的额度。 |
| OFFSET 分页，结果上限 10,000 条 | 浏览需要页码和总数；馆藏规模小；两种排序都有索引覆盖（`(status, title)`、`(status, created_at)`，主键作为第二排序条件）。上限防止单个请求让数据库跳过上百万行。 | 超过上限时，读者应该用关键词缩小范围。如果确实需要深分页：**延迟关联**（先只在索引上翻页取 id，再取这些行）可以保留页码；**游标分页**（`WHERE (title, id) > (:lastTitle, :lastId)`，返回 `nextCursor`）的成本与翻页深度无关，但不能跳页、没有总数，适合无限滚动。两者对关键词搜索都无效：`LIKE '%term%'` 无论怎么分页都要扫全表，那时应该引入 OpenSearch。 |
| 对外暴露自增 ID | 简单 | 会暴露数据量。公开 API 应使用随机 ID 或雪花 ID。 |

**隐私。**阅读历史属于个人数据，可以推断出一个人的健康状况、宗教信仰或政治倾向。服务除了不透明的用户 ID 外不存储任何个人数据；借阅记录只有本人可见；日志里只记录 ID，不会把用户 ID 和书名记在一起。

**阅读内容（未实现）。**书籍文件存放在私有的对象存储中。借阅期间，服务签发一个短时效的签名链接，有效期取"15 分钟"和"到期时间"中较早的一个。链接泄露后很快失效，还书后不再签发新链接。DRM 是另一个独立的话题。

---

## 9. 演进路线：会增加什么，以及何时增加

| 信号 | 变化 |
|---|---|
| 部署多个实例 | 把限流移到网关，或改用基于 Redis 的限流（Lua 脚本实现令牌桶）。 |
| 浏览给数据库带来压力 | 缓存书籍元数据（先 Caffeine，再 Redis），每次写数据库后删除对应缓存。`availableCopies` 变化太频繁，不缓存。浏览走只读副本。 |
| 出现特别热门的新书（成千上万人争抢同一行） | 按书做热点限流（如 Sentinel）；在缓存里记录"已借完"，让请求提前返回；必要时把计数拆成多行，或在 Redis 中预扣并与数据库对账。 |
| 通知、推荐或数据分析需要借阅事件 | 在同一个事务中把事件写入 outbox 表，再转发到消息队列；消费者按事件 ID 保证幂等。 |
| 需要到期自动归还 | 定时任务利用 `(status, due_at)` 索引扫描到期的借阅，小批量处理，每条借阅一个事务，同一时间只有一个执行者（ShedLock 或 `SKIP LOCKED`），复用幂等的还书逻辑。 |
| 借阅表达到数千万行 | 小批量归档 N 个月前已归还的借阅；之后按 `user_id` 分库分表，使读者的所有查询和配额都落在同一个分片上。 |
| 需要全文检索和相关度排序 | 把馆藏数据同步到 OpenSearch。 |

## 10. 已知局限 / 后续步骤

- 用 Testcontainers 在 MySQL 上运行集成测试和并发测试。
- 接入真正的鉴权（JWT 资源服务器），而不是信任网关传入的请求头。
- 续借、预约排队、到期自动归还和到期提醒。
- 馆藏管理：上下架和授权份数调整，包括授权份数减少到低于借出数量时如何处理。
- 针对份数和配额不变量的对账任务。
- 压测，据此确定连接池和线程池的大小。
- 确定目标部署平台后，补充部署打包（容器镜像、健康探针）。
