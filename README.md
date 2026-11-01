# E-Library Service

A backend service for an e-library of digital content: **e-books and journal issues**. Readers can browse the catalogue, see an item's details, borrow and return items, and list what they currently have on loan.

Java 11 · Spring Boot 2.7 · MyBatis · Flyway · in-memory H2 (any MySQL 8 via configuration)

---

## 0. Scope

**Core (what the brief asks for):** the five use cases, the domain model, API design, error handling and tests. That includes concurrency safety, because without it "borrow" is simply incorrect under load: the last copy could be lent twice.

**Small additions, kept on purpose, each under ~50 lines:**

- per-user rate limiting on borrow / return;
- a `traceId` on every request, log line and error;
- loan events feeding logs and metrics.

Each makes a design point (protecting writes, operability, a seam for future integration) and is explained in the trade-offs below.

**Deliberately not built:** authentication, reading / content delivery, catalogue management, caching, messaging, deployment files. Sections 8 and 9 explain why and when each would be added.

---

## 1. Quick start

```bash
mvn spring-boot:run            # JDK 11+. No database or other setup needed: uses in-memory H2 with demo data
mvn verify                     # runs all tests, including the concurrency tests
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- To inspect the in-memory data, start with `--spring.h2.console.enabled=true` and open http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:elibrary`, user `sa`). It is off by default because the console can connect to arbitrary JDBC URLs.

To run against MySQL 8 instead, point the standard Spring properties at it. The same Flyway migrations create the schema:

```bash
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/elibrary?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
SPRING_DATASOURCE_USERNAME=elibrary SPRING_DATASOURCE_PASSWORD=... mvn spring-boot:run
```

### Try it

```bash
curl 'localhost:8080/api/v1/books?keyword=java&size=5'
curl 'localhost:8080/api/v1/books?format=JOURNAL'
curl  localhost:8080/api/v1/books/13
curl -X POST localhost:8080/api/v1/loans -H 'X-User-Id: 7' -H 'Content-Type: application/json' -d '{"bookId":1}'
curl  localhost:8080/api/v1/me/loans -H 'X-User-Id: 7'
curl -X POST localhost:8080/api/v1/loans/1/return -H 'X-User-Id: 7'
```

Demo data:

- items 1–12 are e-books and 13–15 are journal issues;
- book 1 has a single licence, so a second reader borrowing it gets `409 BOOK_UNAVAILABLE`;
- book 12 is off the shelf.

---

## 2. API

| Use case | Request | Success | Errors |
|---|---|---|---|
| Browse books | `GET /api/v1/books?keyword=&format=EBOOK\|JOURNAL&category=&sort=TITLE\|NEWEST&page=1&size=20` (`size` ≤ 100, `page × size` ≤ 10,000) | 200, paged | 400 |
| Book details | `GET /api/v1/books/{bookId}` | 200 | 404 `BOOK_NOT_FOUND` |
| Borrow | `POST /api/v1/loans` `{"bookId":1}` | 201 + `Location` | 404 `BOOK_NOT_FOUND`, 409 `BOOK_UNAVAILABLE` / `ALREADY_BORROWED` / `LOAN_LIMIT_EXCEEDED`, 422 `BOOK_NOT_BORROWABLE` |
| Return | `POST /api/v1/loans/{loanId}/return` | 200 | 404 `LOAN_NOT_FOUND`, 409 `LOAN_ALREADY_RETURNED` |
| One of my loans | `GET /api/v1/loans/{loanId}` | 200 | 404 `LOAN_NOT_FOUND` |
| My current loans | `GET /api/v1/me/loans` | 200, soonest due first | |

Loan endpoints need the `X-User-Id` header (otherwise `401 UNAUTHENTICATED`). Borrow and return are rate-limited per user (`429 TOO_MANY_REQUESTS` + `Retry-After`).

Every error uses the same body, and every response carries an `X-Trace-Id` header:

```json
{ "code": "BOOK_UNAVAILABLE", "message": "no copies of book 1 are available right now",
  "traceId": "9f2c4e...", "timestamp": "2026-11-01T10:00:00Z" }
```

Validation errors add a `violations` list (`[{"field":"size","message":"must be less than or equal to 100"}]`).

**API design choices**

- **A loan is a resource.** Borrowing creates one (`POST /loans`). Returning changes its state rather than deleting it, so the history is kept.
- **Identity never comes from the URL or body.** `/me/loans` and the ownership checks use the caller's identity only, so editing a request cannot expose someone else's loans.
- **HTTP status codes carry meaning.** Gateways, monitoring and client retry logic can act on them (retry 429 and 5xx, never 4xx). The `code` field tells clients the exact reason.
- **Another user's loan returns 404, not 403**, so the API does not reveal which loan ids exist.

---

## 3. Assumptions

| Question left open by the brief | Assumption |
|---|---|
| E-books and journals: what differs? | A journal is lent **per issue** (e.g. *Communications of the ACM*, Vol. 67 No. 3). Both formats follow the same lending rules. They differ only in identity and description: an e-book has an ISBN and an author; a journal issue has an ISSN, an issue label and a publication date. The API keeps the brief's word "books" for any lendable item. |
| How many people can read the same item at once? | Each item has a number of lending **licences** (`totalCopies`). One licence serves one reader at a time, which is how library e-book and e-journal licensing usually works. |
| Loan period and overdue? | 14 days (configurable). "Overdue" is computed when a loan is read. Loans are not returned automatically (see Evolution). |
| Limit per reader? | 5 books on loan at the same time (configurable). |
| Borrow the same book twice? | Not while the first loan is active. It can be borrowed again after returning it. |
| Who is the user? | Authentication is out of scope. An API gateway is assumed to authenticate callers and pass the user id in `X-User-Id`. The service must only be reachable through the gateway, and the gateway must strip any client-sent `X-User-Id`. |
| Books taken off the shelf? | Hidden from browsing and cannot be borrowed. Existing loans keep working and their details stay visible. |
| How are books added? | Seed data (Flyway). Catalogue management is out of scope. |
| "Reading" the content? | Out of scope. Design notes are in section 8. |

---

## 4. Design

### Architecture

A single deployable, **packaged by layer**:

```
com.example.elibrary
├── controller/  HTTP endpoints: translate requests, validate input
├── dto/         request and response bodies (the API contract)
├── service/     use cases and transactions (LoanService, BookQueryService), lending policy, event listener
├── domain/      Book, Loan, statuses, business rules and business exceptions
├── mapper/      MyBatis mappers (SQL in resources/mapper/*.xml) and query result types
└── common/      error handling, traceId, caller identity, rate limiting, config
```

Dependencies point one way: controller → service → mapper, and everything may use domain. Domain depends on nothing else, so its rules are unit-tested without Spring or a database. Controllers never return domain objects directly: they map them to `dto` classes, so the API contract can change independently of the internal model.

- **Domain objects** hold the business rules: Book knows whether it can be lent, Loan knows its due date and whether it is overdue.
- **Mappers** hold the SQL. MyBatis keeps the concurrency-critical SQL explicit and reviewable, where JPA would hide it.
- The mapper interfaces act as the repository boundary. A separate repository interface on top of them would add code without benefit at this size.

Why by layer: the service has only two closely related concepts (a loan always involves a book), so splitting by feature would add nesting without giving real isolation. When the catalogue and lending grow into separate concerns, the natural next step is to split by feature (`book/`, `loan/`) and then into services.

### Domain model

- **Book**: any lendable item in the catalogue, with `totalCopies` (licences) and `availableCopies`. Its `format` is `EBOOK` (ISBN, author) or `JOURNAL` (ISSN, issue, publication date).
- **Loan**: one user borrowing one book. Status is `ACTIVE` or `RETURNED`, with `borrowedAt`, `dueAt` and `returnedAt`. Loans are never deleted.
- **UserLoanQuota**: one row per user holding their count of active loans (see section 5).

**E-books and journals: one entity with a format, not a class hierarchy.** Everything the service *does* with an item depends only on its licences and its shelf status: browsing, borrowing, returning and limits. Format matters only for identity and display. Three options were considered:

| Option | Verdict |
|---|---|
| One table and entity with a `format` column | **Chosen.** One code path for lending; adding a format is a new enum value. A `CHECK` constraint makes each format carry exactly its own fields (e.g. a journal issue without an issue label is rejected by the database). |
| Inheritance (`EBook` / `JournalIssue` subclasses, single-table or joined) | More types and mapping code, but no behaviour that actually differs. |
| Separate tables and APIs per format | Duplicates the lending logic and the concurrency guarantees, and a loan would have to point at two kinds of things. |

If the formats start to *behave* differently, a class hierarchy (or polymorphic policies) would earn its keep. Examples: journals readable online only, or different loan periods per format.

**Invariant:** `book.total_copies = book.available_copies + count(ACTIVE loans of the book)`.

`availableCopies` is stored as a denormalised counter instead of being computed with `COUNT(*)` on every read. A stored counter can be changed atomically by one conditional `UPDATE`. A computed count needs locking: "count, then insert" races. Reads are also cheaper. The risk is drift between the counter and the loans, which is contained by four measures:

- all changes happen in the same transaction;
- a `CHECK` constraint keeps the counter between 0 and `total_copies`;
- invariant breaches are logged as errors;
- a periodic reconciliation job is listed under Evolution.

---

## 5. Concurrency and consistency

Correctness relies only on the database, never on JVM locks or caches, so it holds with any number of instances:

| Risk | Protection |
|---|---|
| Two readers take the last copy | `UPDATE book SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0`. The check and the change are one atomic statement; 0 rows updated means none left. |
| One reader borrows the same book twice concurrently | Unique index `(user_id, book_id, active_flag)`. `active_flag` is 1 while the loan is active and NULL after return. Unique indexes allow many NULLs, so the full history is kept. |
| One reader exceeds the limit by borrowing different books concurrently | `UPDATE user_loan_quota SET active_count = active_count + 1 WHERE user_id = ? AND active_count < max`. "Count, then check" would let both requests pass. |
| Double return, or retrying a return | `UPDATE loan ... WHERE id = ? AND user_id = ? AND status = 'ACTIVE'`. The second attempt changes nothing; `user_id` doubles as the ownership check. |
| A step fails halfway | Each use case is one transaction, so a failure rolls back every earlier step (copy, quota). |
| Deadlocks | Fixed lock order: both use cases update the user's quota row first (borrow: quota → book → loan; return: quota → loan → book). The same user's writes are serialised at step one, and different users only meet on the book row. Should the database still report a deadlock or lock timeout, it is mapped to `409 CONCURRENT_CONFLICT` (safe to retry). |

**Rejected alternatives**

- `synchronized` / `ReentrantLock`: only works inside one JVM. Inside a `@Transactional` method the lock is also released *before* the commit, so a second thread can still read stale data.
- `SELECT ... FOR UPDATE`: correct, but holds the row lock for the whole transaction and needs a read-then-write. The conditional update does the same in one statement.
- Optimistic locking with a `version` column: contended borrows of a popular book would mostly fail and retry. A conditional update needs no retry.
- A distributed lock (e.g. Redisson): adds a network hop and a failure point to protect something one SQL statement already guarantees. A lease can also expire during a GC pause, leaving two holders.

`LoanConcurrencyTest` proves the invariants by starting many threads at the same moment:

- 50 readers race for the last copy: exactly 1 succeeds;
- 60 readers race for 20 copies: exactly 20 succeed;
- one new reader borrows 12 books at once: exactly 5 succeed (the limit);
- the same reader borrows the same book 10 times at once: exactly 1 loan is created;
- the same loan is returned 10 times at once: exactly 1 copy goes back.

---

## 6. Error handling, logging and operations

- **Errors.** Business failures are typed exceptions (`BookUnavailableException`, ...) carrying an `ErrorCode`, and each code has one HTTP status. A single `@RestControllerAdvice` turns every exception into the error body. Unexpected errors are logged with a stack trace, while the client only gets `500 INTERNAL_ERROR` and the `traceId`.
- **Rate limiting.** Borrow and return use a per-user token bucket (5 requests in a burst, then 1 per second). Buckets are held in a bounded, expiring Caffeine cache, so memory cannot grow without limit. Reads are not limited.
- **Observability.**
  - Every request gets a `traceId`; a valid `X-Trace-Id` sent by the caller is reused.
  - The `traceId` appears in logs, response headers and error bodies.
  - Logs are JSON under the `prod` profile.
  - Micrometer counters: `elibrary.loans{event}` and `elibrary.requests.rejected{code}`, e.g. a spike of `BOOK_UNAVAILABLE` signals a popular title.
  - Health checks are available at `/actuator/health`.
- **Time.** All times are UTC `Instant`s from an injected `Clock`, so due dates and overdue logic are tested deterministically.
- **Schema.** Flyway migrations are shared by H2 and MySQL.
- **Shutdown.** Graceful shutdown is enabled: in-flight requests finish before the process exits.

---

## 7. Tests

| Level | What |
|---|---|
| Unit | `Book` and `Loan` rules (due date, overdue boundary), the token bucket (fake clock), keyword escaping |
| Unit, interleavings (`LoanServiceQuotaTest`, mocks) | lazy creation of the quota row under every race outcome. A stress test can only hit these windows by luck; mocks reproduce each interleaving every time. This test caught a real bug: a reader's first concurrent borrows could be told `LOAN_LIMIT_EXCEEDED` while holding no books. |
| API integration (`MockMvc` + H2) | every endpoint, status code and error code; e-books and journal issues (format filter, format-specific fields, lending a journal issue, schema rejecting fields that do not fit the format); ownership; loan limit; paging cap; return restores copy and quota; loans of a book taken off the shelf; overdue via a movable test clock; rate limiting |
| Concurrency (real threads + H2) | the five race scenarios above |

Tests create their own books and users, so they are independent of each other and of the demo data.

---

## 8. Trade-offs and what I left out on purpose

| Decision | Why | Cost / when to revisit |
|---|---|---|
| H2 by default | Reviewers can run everything with one command | H2 and MySQL differ in locking details. The concurrency SQL only relies on atomic single-row updates and unique indexes, which behave the same in both, but I would run the tests on MySQL via Testcontainers next. |
| No Redis, no message queue | Nothing here needs them. Each would bring its own consistency and failure-mode problems. | Event and rate-limit seams already exist (see Evolution). |
| Overdue computed on read | Always accurate, no scheduler needed | Copies are not freed automatically at the due date. |
| In-process events (`@TransactionalEventListener(AFTER_COMMIT)`) | Decouples side effects (today: logs and metrics) at zero infrastructure cost | Not durable. Anything that must not be lost needs an outbox. |
| Per-instance rate limiting | Simple and dependency-free | With N instances, a user gets N times the limit. |
| Offset paging, capped at 10,000 results | Browsing needs page numbers and a total; the catalogue is small; both sort orders are covered by an index (`(status, title)`, `(status, created_at)`, with the primary key as tie-breaker). The cap stops a single request from making the database skip millions of rows. | Beyond the cap, readers narrow the search instead. If deep paging becomes a real need: a **deferred join** (page through ids on the index only, then fetch those rows) keeps page numbers; **keyset paging** (`WHERE (title, id) > (:lastTitle, :lastId)`, returning a `nextCursor`) makes cost independent of depth but loses page jumps and totals, so it suits infinite scroll. Neither helps keyword search: `LIKE '%term%'` scans the table regardless of paging, and the fix there is OpenSearch. |
| Auto-increment ids exposed | Simple | Reveals volumes. Use random or snowflake ids for a public API. |

**Privacy.** Reading history is personal data: it can reveal health, religion or political views. The service stores no personal data besides an opaque user id. Loans are only visible to their owner. Logs contain ids, never a user id next to a book title.

**Reading content (not implemented).** Book files would live in private object storage. While a loan is active, the service would issue a short-lived signed URL: the earlier of 15 minutes or the due date. A leaked link expires quickly, and returning a book stops new links. DRM would be a separate concern.

---

## 9. Evolution: what I would add, and when

| Signal | Change |
|---|---|
| More than one instance | Move rate limiting to the gateway or a Redis-backed limiter (Lua token bucket). |
| Browsing load on the database | Cache book metadata (Caffeine, then Redis). Delete the cache entry after each DB write. Never cache `availableCopies`, which changes too often. Use read replicas for browsing. |
| A very popular new title (thousands competing for one row) | Use per-book hot-key rate limiting (e.g. Sentinel). Remember "sold out" in a cache so requests return early. If needed, split the counter into several rows or pre-decrement in Redis with DB reconciliation. |
| Notifications, recommendations or analytics need loan events | Write events to an outbox table in the same transaction and relay them to a message queue. Make consumers idempotent by event id. |
| Copies should come back at the due date | Run a scheduled sweep using the `(status, due_at)` index. Process small batches, one transaction per loan, with a single active runner (ShedLock or `SKIP LOCKED`). Reuse the idempotent return logic. |
| Loan table reaches tens of millions of rows | Archive returned loans older than N months in small batches. Later, shard by `user_id`: every reader query and the quota stay on one shard. |
| Full-text search and relevance | Sync the catalogue to OpenSearch. |

## 10. Known limitations / next steps

- Run the integration and concurrency tests on MySQL with Testcontainers.
- Real authentication (JWT resource server) instead of trusting a gateway header.
- Renewals, holds/waiting list, automatic return and reminders.
- Catalogue management: shelving and licence changes, including what happens when licences are reduced below the number on loan.
- A reconciliation job for the copies and quota invariants.
- Load test to size the connection and thread pools.
- Packaging for deployment (container image, health probes), once the target platform is known.
