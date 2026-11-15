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
