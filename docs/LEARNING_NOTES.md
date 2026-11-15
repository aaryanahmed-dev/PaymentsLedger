# Learning notes

One section per phase: what was built, where the code lives, the concepts behind it, and
interview questions worth being able to answer. Paths are relative to
`backend/src/main/java/com/aaryanahmed/paymentsledger/` unless they start with another folder.

---

## Phase 0 – Housekeeping

### What changed
- `application.properties` now reads `DB_URL`, `DB_USER`, `DB_PASSWORD` and `PORT` from environment
  variables, each with a local default (`${DB_URL:jdbc:mysql://localhost:3306/payments_ledger}`
  means "use the env var `DB_URL`, or this value if it is not set").
- `spring.jpa.show-sql` was removed: it printed every SQL statement, which drowns the test output.
- `spring.jpa.open-in-view=false`: by default Spring keeps a database session open for the whole
  HTTP request. Turning it off means all database work has to happen inside the service layer,
  which is where the transactions are. It also frees the database connection sooner.
- `GlobalExceptionHandler` now extends `ResponseEntityExceptionHandler`, so Spring's built-in errors
  (malformed JSON, failed validation, unsupported method) come back as `ProblemDetail` JSON too,
  and validation failures include an `errors` object naming each bad field.

---

## Phase 1 – Transfers and the double-entry ledger

### What was built
Money can now move. A transfer debits one account, credits another, and records the movement
as two ledger entries that cancel out. A deposit is just a transfer from a special EXTERNAL
"funding" account, so even money entering the system obeys the same rule.

| Endpoint | What it does |
|---|---|
| `POST /transfers` | Move money between two accounts |
| `GET /transfers/{id}` | Look up one transfer |
| `POST /accounts/{id}/deposits` | Transfer from the funding account of the same currency |
| `GET /accounts/{id}/entries?page=0&size=20` | An account's ledger entries, newest first |

### Where the key code lives
- `account/Account.java` – `debit()` and `credit()`. The overdraft rule lives here, in the entity,
  not in the service. There are no setters, so the only way to change a balance is through
  these two methods, and neither can leave the account in an illegal state.
- `transfer/TransferService.java` – the one `@Transactional` method that does the whole transfer.
- `transfer/Transfer.java`, `ledger/LedgerEntry.java` – immutable records of what happened.
- `ledger/LedgerService.java` – paginated history.
- `common/GlobalExceptionHandler.java` – maps exceptions to HTTP status codes.
- `resources/db/migration/V5__add_usd_gbp_funding_accounts.sql` – funding accounts for USD and GBP.
- Tests: `backend/src/test/.../LedgerInvariants.java` and `transfer/TransferFlowIntegrationTest.java`.

### Concepts

**Double-entry bookkeeping.** Every movement of money is written twice: `-30.25` on the sender
and `+30.25` on the receiver. Two things then must always be true (the *ledger invariants*):
1. All ledger entries added together equal exactly zero.
2. Each account's balance equals the sum of its own entries.

If either is ever false, there is a bug, and you can find it, because entries are never edited
or deleted. The `balance` column is really a cache of "sum of my entries" kept for speed.

**`@Transactional`.** Spring wraps the method in a database transaction: it begins before the
method runs, commits if the method returns normally, and rolls back if it throws a
`RuntimeException`. A transfer makes five writes (one transfer row, two entries, two balance
updates). Without a transaction, a crash after the third write would leave money half-moved.
The test `rejectedTransferChangesNothing` shows the all-or-nothing behaviour.

**Dirty checking.** The service never calls `accountRepo.save(from)`. Entities loaded inside a
transaction are *managed*: Hibernate keeps a snapshot of them, compares at commit time, and
issues `UPDATE` statements for whatever changed.

**IDs instead of object references.** `Transfer` stores `fromAccountId` (a `Long`) rather than
an `Account` object with `@ManyToOne`. That keeps the entities simple and avoids lazy-loading
surprises. The database foreign keys still guarantee the accounts exist.

**Validation in two layers.** The request records use Bean Validation (`@NotNull`, `@Positive`,
`@Digits(fraction = 2)`) so bad input is rejected with a 400 before any code runs. The service
and the entity check again, because they can also be called from other code (a deposit calls
`transfer()` directly), and the domain should protect itself.

**Status codes.** 400 = the request itself is malformed (missing field, negative amount, same
account twice). 404 = the thing does not exist. 422 = the request is well-formed but a business
rule refuses it (insufficient funds, currency mismatch, no funding account for that currency).

**Testcontainers.** The tests start a real MySQL 8.4 in Docker (`TestcontainersConfiguration`),
run the real Flyway migrations against it, and throw it away afterwards. An in-memory database
such as H2 would be faster but behaves differently from MySQL, especially for the locking
added in Phase 3.

### Known limitation (fixed in Phase 3)
Two simultaneous transfers from the same account can both read the same balance and both
succeed, overdrawing the account. Nothing here locks the account rows yet.

### Interview questions

**Why keep ledger entries if you already store the balance?**
The balance tells you *what* an account holds; the entries tell you *why*. They are an
append-only audit trail, and they let you verify the balance independently. Storing only a
balance means a bug can silently create or destroy money and you would have no way to notice
or to work out what the right number is.

**Why `BigDecimal` and not `double`?**
`double` is binary floating point and cannot represent most decimal fractions exactly:
`0.1 + 0.2` is `0.30000000000000004`. With money those errors accumulate. `BigDecimal` is exact
decimal arithmetic. Compare with `compareTo`, not `equals`: `equals` also compares scale, so
`new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is `false`.

**What does `@Transactional` actually do, and when does it not work?**
Spring creates a proxy around the bean. The proxy opens a transaction, calls the real method,
then commits or rolls back. Because it relies on the proxy, it does not apply when a method
calls another method of the *same* class (`this.other()` skips the proxy), on private methods,
or by default for checked exceptions (only `RuntimeException` and `Error` roll back). In this
project `deposit()` calls `transfer()` on the same object, which is fine because `deposit()`
already started a transaction and `transfer()` simply runs inside it.

**Why is the overdraft check in `Account` rather than `TransferService`?**
So the rule cannot be bypassed. Any code path that reduces a balance has to go through
`debit()`. If the check lived in the service, a second service (reversals, say) could forget it.

**Why does a deposit come from an account that is allowed to go negative?**
So that deposits follow the same double-entry rule as everything else and the entries still sum
to zero. The funding account's negative balance is meaningful: it is the total amount of money
that has entered the system from outside.

---

## Phase 2 – Tests

### What was built
54 tests in two layers:

- **Unit tests** (`account/AccountTest`): the debit and credit rules, with no Spring and no
  database. They create an `Account` with `new` and call its methods. All 15 run in well under a second.
- **Integration tests** (`AccountControllerTest`, `TransferControllerTest`, `LedgerControllerTest`,
  `TransferFlowIntegrationTest`): HTTP requests through the whole application into a real MySQL.
  Every endpoint has a success test and a test for each way it can fail.

All test code is under `backend/src/test/java/com/aaryanahmed/paymentsledger/`.

### Where the key code lives
- `IntegrationTest.java` – base class. Starts the application once, gives every test `mockMvc`,
  and has helpers (`createAccount`, `deposit`, `transfer`) so tests read like a story.
- `TestcontainersConfiguration.java` – the MySQL container.
- `LedgerInvariants.java` – the reusable check that entries sum to zero and balances match.
  `TransferControllerTest` runs it in `@AfterEach`, so it is checked after every test in that
  class, including the ones where the request was rejected.

### Concepts

**The test pyramid.** Many fast unit tests for logic, fewer slower integration tests for wiring.
The overdraft rule has many edge cases (zero, negative, exact balance, one cent over), so they
are tested at unit level where each case costs a millisecond. The integration tests then only
need to prove the rule is connected to HTTP: one request, one 422.

**MockMvc.** It calls the controllers through Spring's real request handling (JSON parsing,
validation, exception handlers) without opening a network port. `jsonPath("$.balance")` reads
a field out of the JSON response.

**Arrange, act, assert.** Each test sets up its own data, does one thing, then checks the
result. The blank lines in the tests separate those three steps.

**Test isolation without cleanup.** All integration tests share one database and never delete
anything. That works because each test creates its own accounts and only asserts on those.
It also makes the invariant check stronger: it runs over everything every earlier test left behind.

**Parameterized tests.** `@ParameterizedTest` with `@ValueSource` runs the same test once per
value, for example each invalid amount (`0`, `-5.00`, `10.001`, `"abc"`, `null`).

### Interview questions

**Why Testcontainers rather than H2 or mocking the repositories?**
The risky parts of this project are the database behaviours: transactions, constraints, and
(next phase) row locks. H2 does not behave like MySQL for those, and mocks do not behave like
a database at all, so tests could pass while production fails. The cost is speed: the
container takes a few seconds to start, once per test run.

**What is the difference between a unit test and an integration test?**
A unit test checks one class in isolation, with no framework or I/O. An integration test checks
that several real parts work together, here controller, validation, service, JPA and MySQL.
Unit tests tell you *which* rule broke; integration tests tell you the feature really works.

**How do you test that a failed operation left no trace?**
Assert on state after the failure, not only on the status code. `rejectedTransferChangesNothing`
checks that both balances are unchanged, that the receiver has no entries, and that the
ledger invariants still hold after a 422.

**Why not roll back each test with `@Transactional` on the test class?**
With MockMvc the test and the service would share one transaction that never commits, so the
tests would not exercise real commit behaviour, and it cannot work at all for the concurrency
tests, where several threads each need their own transaction.

---

## Phase 3 – Concurrency

### What was built
Transfers are now safe when many happen at once. The tests were written first and run against
the Phase 1 code, where all three failed:

| Test (`transfer/TransferConcurrencyTest`) | Result before the fix |
|---|---|
| 100 concurrent withdrawals of 10.00 from a balance of 100.00 | Only 86 of the expected 90 were rejected for insufficient funds; the rest hit MySQL deadlock errors |
| 50 transfers A→B and 50 B→A at the same time | MySQL deadlock errors |
| 50 concurrent deposits to one account | MySQL deadlock errors |

After the fix all three pass, and they passed on three consecutive runs.

### Where the key code lives
- `account/AccountRepository.java` – `findByIdForUpdate`, annotated `@Lock(PESSIMISTIC_WRITE)`.
- `transfer/TransferService.java` – `transfer()` locks both accounts, lowest id first, before
  reading either balance. `deposit()` looks up the currency and the funding account's id
  without loading entities.
- `backend/src/test/.../transfer/TransferConcurrencyTest.java` – the tests and the
  `runConcurrently` helper built on `ExecutorService` and `CountDownLatch`.

### Concepts

**The race condition (lost update).** A transfer is read-then-write: read the balance, check
it, write the new balance. Two transactions can both read 100.00, both decide 10.00 is
affordable, and both write 90.00. One withdrawal has vanished. With enough of them the account
goes negative even though every individual check passed.

**Pessimistic locking.** `@Lock(PESSIMISTIC_WRITE)` makes Hibernate run
`SELECT ... FOR UPDATE`. MySQL locks that row until the transaction commits or rolls back.
A second transaction asking for the same row waits, and when it gets the row it reads the
balance the first one committed. The check and the write can no longer be interleaved.

**Deadlock and lock ordering.** Transfer A→B locks A then B. Transfer B→A locks B then A.
If each gets its first lock, each waits forever for the other's: a deadlock. MySQL detects it
and kills one transaction. The cure is to make every transaction take locks in the same
order. Here that is ascending account id, so A→B and B→A both lock the lower id first; one
gets it and the other waits its turn.

**Why the unlocked code deadlocked rather than just losing updates.** Inserting a `transfers`
row takes a *shared* lock on the two account rows (because of the foreign keys). Two
transactions could both hold the shared lock and then both need to upgrade it to an
exclusive lock for the `UPDATE`, each blocked by the other.

**A Hibernate trap: the persistence context.** Within one transaction Hibernate keeps one
object per row. If `deposit()` had loaded the `Account` normally and then `transfer()` asked
for it again with a lock, MySQL would lock the row, but Hibernate would hand back the object
it already had, with the balance read *before* the lock. That is the lost update again, by a
back door. So `deposit()` only fetches the currency string and the funding account's id, and
the locked read is the first time the entity is loaded.

**`CountDownLatch`.** Threads started in a loop do not start together. Each test thread
counts down `ready` and then blocks on `start`; when all are waiting, the test releases them
with one `start.countDown()`. That gives the most overlap and makes the race reproducible.

### Interview questions

**Pessimistic or optimistic locking: which and why?**
Optimistic locking (`@Version`) lets everyone proceed and fails the loser at commit, who must
retry. It suits low contention. Pessimistic locking makes the loser wait instead. For a
payments ledger, hot accounts are normal (a merchant, the funding account), so optimistic
locking would cause many retries, and the retry logic would have to be written and tested.
Pessimistic locks are simpler and correct. The trade-off is throughput: transfers touching the
same account run one at a time.

**How do you prevent deadlocks?**
Acquire locks in a consistent global order, here by ascending account id. Also keep
transactions short, and lock everything needed up front rather than part-way through.

**What isolation level does MySQL use, and does it prevent this race by itself?**
InnoDB defaults to REPEATABLE READ. It gives each transaction a consistent snapshot for plain
reads, but two transactions can still read the same balance and both write, so it does not
prevent lost updates. `SELECT ... FOR UPDATE` does, because it is a locking read of the latest
committed row.

**How did you test it?**
Tests first. 100 threads released together by a `CountDownLatch` against a real MySQL, then
assertions on the outcome: exactly 10 of 100 withdrawals succeed, the balance is exactly zero,
no deadlock exceptions, and the ledger invariants hold. They failed before the fix and pass
after it, repeatedly.

**Would this work with several application instances?**
Yes. The lock is held by the database, not by the JVM, so it works across any number of
instances. A Java `synchronized` block would only protect one instance.

---

## Phase 4 – Idempotency and reversals

### What was built
- `POST /transfers` and `POST /accounts/{id}/deposits` now **require** an `Idempotency-Key`
  header (missing, blank or longer than 255 characters is a 400). Sending the same request
  again with the same key returns the original response and moves no more money.
- The same key with a *different* request is a 409.
- `POST /transfers/{id}/reverse` creates the opposite transfer, linked to the original through
  the new `reversal_of` column. A transfer can be reversed once; a reversal cannot be reversed
  (both 409). A reversal fails with 422 if the receiver no longer has the money.

### Where the key code lives
- `idempotency/IdempotencyService.java` – `execute(key, request, responseType, action)`.
- `idempotency/IdempotencyKeyRepository.java` – `insertIfAbsent` (`INSERT IGNORE`).
- `transfer/TransferController.java` – builds the "same request" string and wraps the service call.
- `transfer/TransferService.java` – `reverse()`, and the private `execute()` shared by
  transfers and reversals.
- Migrations `V6__create_idempotency_keys.sql`, `V7__add_transfer_reversal_of.sql`.
- Tests: `idempotency/IdempotencyTest`, `transfer/TransferReversalTest`.

### Concepts

**Why idempotency.** A client sends a payment and the connection drops before the response
arrives. Did it go through? The client cannot know, and retrying might charge twice. With an
idempotency key the client generates one random key per *intended* payment and can retry
safely: the server does the work once and repeats the answer.

**How it works here.** In one transaction:
1. `INSERT IGNORE` a row with the key and a SHA-256 hash of the request.
2. If the insert happened, this is a new request: run the transfer, then store the JSON response in that row.
3. If the row already existed: compare hashes. Different means 409. Same means return the stored response.

Because the key row and the transfer commit together, there is never one without the other.
If the transfer fails (for example insufficient funds), the key row is rolled back too, so
the client may retry with the same key once the problem is fixed.

**Why the unique constraint matters.** A check like "does this key exist? no? then insert"
has the same race as the balance check in Phase 3: two requests can both see "no". The unique
index makes the database the referee. When two transactions insert the same key, the second
`INSERT` *waits* for the first transaction to finish. If the first commits, the second sees a
duplicate and replays the stored response. If the first rolls back, the second proceeds.
The test `concurrentDuplicatesCreateOneTransfer` sends 20 copies at once and gets 20 identical
responses and one transfer.

**Why the insert must be the first statement.** Under MySQL's REPEATABLE READ, a transaction's
plain `SELECT`s see the database as it was at its *first* plain read. If the transaction read
something before the `INSERT`, then waited on the other request, it would not be able to see
the response the other request had just committed. The insert takes no snapshot, so the
lookup that follows it sees the committed row.

**What is hashed.** Not the raw body, but a string describing the request, such as
`transfer:12:13:10.5`. So `10.5` and `10.50` count as the same request, and a key used for a
deposit cannot be replayed against a transfer.

**Reversals instead of deletes.** A ledger is append-only. To undo a transfer you add a new
one in the opposite direction, so the history shows both that it happened and that it was undone.

**Two layers of "at most once" for reversals.** `reverse()` locks the original transfer row
(`SELECT ... FOR UPDATE`), so simultaneous reversals take turns and the second one finds the
first one's reversal and returns 409. Underneath, `UNIQUE (reversal_of)` means the database
would refuse a second reversal even if the application logic were wrong.

### Limitations worth knowing
- Only successful responses are stored. Stripe also stores and replays errors.
- Keys are never expired or deleted. A real system would purge them after about 24 hours.
- `INSERT IGNORE` is MySQL-specific.

### Interview questions

**What does idempotent mean?**
Doing it once or many times has the same effect. `GET`, `PUT` and `DELETE` are idempotent by
definition; `POST` is not, which is why payment APIs add idempotency keys.

**Who generates the key, and why not the server?**
The client, before the first attempt, typically a UUID. The point is to recognise a retry of
a request whose response the client never received, so the identifier has to exist on the
client before anything is sent.

**Why store a hash of the request?**
To catch a client bug: reusing a key for a different payment. Silently returning the old
response would tell the client its new payment succeeded when nothing happened. A 409 makes
the mistake visible.

**Two identical requests arrive at the same millisecond. What happens?**
Both try to insert the key. The unique index lets one through and blocks the other until the
first commits. The second then finds the row, sees the same hash and returns the stored
response. One transfer, two identical responses.

**Why not make the reversal update or delete the original transfer?**
Auditability. Financial records are never rewritten; corrections are new records. It also keeps
the invariants trivially true, since a reversal is just another balanced pair of entries.

---

## Phase 5 – Webhooks via the outbox pattern

### What was built
Other systems can now be told when money moves. A client registers a URL; every transfer then
produces a `transfer.created` event (or `transfer.reversed`) that is POSTed to that URL, signed,
and retried if the receiver is down.

| Endpoint | What it does |
|---|---|
| `POST /webhook-endpoints` `{"url": "https://..."}` | Register a URL. The response contains the generated `secret`, shown only this once. |
| `GET /webhook-endpoints` | List registered endpoints (without secrets) |

What the receiver gets:

```
POST /your/url
Content-Type: application/json
X-Webhook-Event-Id: 42
X-Webhook-Signature: t=1700000000,v1=<64 hex characters>

{"type":"transfer.created","data":{"id":7,"fromAccountId":1,"toAccountId":5,"amount":10.00,...}}
```

### Where the key code lives
- `webhook/OutboxService.java` – `enqueue()` writes the events; `recordDelivered()` / `recordFailure()` save results.
- `transfer/TransferService.java` – calls `outboxService.enqueue(...)` inside the transfer transaction.
- `webhook/WebhookDispatcher.java` – the `@Scheduled` job that sends pending events.
- `webhook/OutboxEvent.java` – retry state and the backoff rule (`markAttemptFailed`).
- `webhook/WebhookSigner.java` – HMAC-SHA256 signing.
- `common/SchedulingConfig.java` – `@EnableScheduling`, switchable off for tests.
- `resources/db/migration/V8__create_webhooks_and_outbox.sql`.
- Tests: `webhook/WebhookTest` (MockWebServer), `OutboxEventTest`, `WebhookSignerTest`.

### Concepts

**The dual-write problem.** After a transfer commits we want to send an HTTP request. Two
separate systems (our database, their server) cannot be updated atomically:
- Send first, then commit: if the commit fails, we announced a transfer that never happened.
- Commit first, then send: if the app crashes in between, the event is lost for ever.

**The outbox pattern.** Do not send anything during the request. Write a row describing the
event into an `outbox_events` table **in the same transaction** as the transfer. Now it is one
database commit: both exist or neither does. A separate background job reads the table and
does the sending, and it can retry as often as needed because the event is safely stored.
`enqueue()` is marked `@Transactional(propagation = MANDATORY)`, which throws if it is ever
called outside a transaction, so this guarantee cannot be broken by accident.

**At-least-once delivery.** If the receiver processes the event but its 200 response is lost,
we will send it again. Duplicates are possible; losing an event is not. That is why every
request carries `X-Webhook-Event-Id`: the receiver remembers the ids it has handled and
ignores repeats. (The same idea as idempotency keys, in the other direction.)

**Exponential backoff.** After a failure, wait before retrying, and double the wait each time:
2s, 4s, 8s, 16s. A receiver that is struggling gets breathing room instead of a flood of
retries. After 5 attempts the event is marked `FAILED` and left alone (a "dead" event that a
human can inspect; `last_error` says why).

**HMAC signatures.** Anyone can POST to a public URL, so the receiver needs proof that a
request came from us. Both sides know the secret. We send
`HMAC-SHA256(secret, timestamp + "." + body)`; the receiver computes the same thing and
compares. Without the secret a valid signature cannot be produced, and changing one byte of
the body changes the signature. Signing the timestamp too lets the receiver reject requests
older than a few minutes, which defeats replaying a captured request.

**No transaction around the HTTP call.** `WebhookDispatcher.deliver()` is not `@Transactional`.
Holding a database connection and row locks while waiting up to 5 seconds on someone else's
server would exhaust the connection pool. Each result is saved in its own short transaction.

**`@Scheduled(fixedDelay)`.** Runs the method on a background thread, waiting the given time
*after* the previous run finishes, so runs never overlap. Tests switch scheduling off
(`scheduling.enabled=false`) and call `dispatchPending()` directly, which makes them
deterministic. To simulate the backoff delay passing, the test rewinds `next_attempt_at`.

### Limitations worth knowing
- One dispatcher instance is assumed. Two instances could both send the same event (harmless
  given at-least-once, but wasteful). The usual fix is `SELECT ... FOR UPDATE SKIP LOCKED`.
- Events are sent one at a time, so one slow endpoint delays the others.
- Endpoints cannot be deleted through the API, secrets are stored in plain text, and there is
  no check against registering internal addresses. All acceptable without authentication, none
  acceptable in production.

### Interview questions

**What problem does the outbox pattern solve?**
Reliably publishing an event about a database change. You cannot atomically commit to a
database and call another system, so you store the event in the database in the same
transaction and deliver it asynchronously. It turns "maybe lost" into "delivered at least once".

**Why not just send the webhook after the transaction commits?**
The process can crash, be redeployed or lose network between the commit and the send, and
then nothing remembers that an event is owed. With the outbox, the debt is recorded durably.

**What delivery guarantee do you provide, and what must receivers do?**
At-least-once. Receivers must be idempotent, typically by storing the event id and skipping
ids they have seen.

**How does the receiver verify a webhook?**
Read `t` and `v1` from the signature header, compute `HMAC-SHA256(secret, t + "." + rawBody)`
over the raw bytes received, compare with `v1` using a constant-time comparison, and reject
if `t` is too old.

**Why exponential backoff rather than retrying immediately?**
Most failures are either momentary or last minutes. Immediate retries waste effort and add
load to a server that is already failing. Backing off spreads retries out; a maximum number
of attempts stops a permanently broken endpoint from being retried for ever.

**How would this change with Kafka?**
The outbox table stays. Only the dispatcher changes: instead of POSTing to URLs it publishes
rows to a topic. The pattern is the same because Kafka has the same dual-write problem.
