# Advanced testing

Read before writing datasource (H2) tests, scheduled job tests, or tests that drive coroutines through virtual time. Moved out of `AGENTS.md`; every rule here is as binding as one there.

### Scheduled job wiring test

Every `Application.register<Svc>Jobs(scheduler)` has a `<Svc>JobsTest` in its microservice module (`BusinessJobsTest`, `AppointmentsJobsTest`, `AuthJobsTest`) — full recipe in `docs/operations/scheduled-jobs.md` → "Adding a new job". Tools: `startScopedApplication(<Svc>Scope) { scoped { mockOp } }` (`testFixtures(projects.core.service)`) boots Koin + the service scope and returns the `Application`; `registeredJobs()` / `runJob(name)` (`testFixtures(projects.library.scheduler)`) inspect and run a `SchedulerConfiguration`'s jobs directly, no timing. Assert the whole name → interval map, and per job both the invocation (with arguments) and that a `Result.failure` propagates — that is what catches a dropped `getOrThrow()`. A module's `testFixtures` source set can read that module's `internal` members (`runJob` reads `SchedulerConfiguration.jobs`), so a fixture never needs a production API widened just for tests. `MonolithJobsTest` (root project, `src/test`) covers the monolith's single shared scheduler: it asserts `registerMonolithJobs` registers exactly the union of each service's own `register<Svc>Jobs` (a cross-service name clash throws at registration, a service missing from the monolith shows up as a map mismatch), and that same-typed operations in different scopes (auth vs business `RotateSigningKeys`) resolve from their own scope — use the `startScopedApplication(AuthScope to { … }, BusinessScope to { … })` overload for several scopes at once. Every job name is `"$<SVC>_SERVICE_NAME-<jobName>"` (`auth-rotateSigningKeys`, `business-rotateSigningKeys`), so names can't clash in the monolith and a log line names its service. `MonolithJobsTest` fails on a job without its service's prefix, and a service that gets its first job must be added to that check.

## Datasource (H2 integration) test conventions

`createTestDatabase(vararg tables: Table)` in `core/data/src/testFixtures` creates a per-test H2 database in `MODE=MySQL`. Call datasource methods inside `suspendTransaction { fixture.sut.method() }` within `runUnitTest { }`. Use `dbQuery`-based methods inside `suspendTransaction {}`. Cache-based methods (those using `mapExceptions` without `dbQuery`) are called directly without wrapping.

### CacheClient in datasource tests

Use `InMemoryCacheClient` from `testFixtures(projects.core.data.cache.impl)` — do NOT use `mockk<CacheClient<String>>`. The relaxed mock returns a non-null mock for `get()` which fails the reified cast. Example fixture:
```kotlin
private class SutFixture {
    val db = createTestDatabase(...)
    val cacheClient = InMemoryCacheClient()
    val sut = MyDataSourceImpl(cacheClient)
}
```
See `PassKeyDataSourceImplTest` and `AppointmentRequestDataSourceImplTest` as reference.

### H2 dialect limitations — untestable operations

These throw `UnsupportedByDialectException` in H2 even with `MODE=MySQL`. Omit their tests and leave a comment in the test file:

| Operation | Used by | Workaround for reads |
|---|---|---|
| `updateReturning` | — | rewrite as `update {}` + a follow-up `selectAll()` read, which H2 supports and makes the method testable (`BusinessDataSourceImpl.updateBusiness` was converted this way) |
| `deleteReturning` | `BusinessDataSourceImpl.deleteUserBusinesses`, `DeviceDataSourceImpl.deleteInactiveDevices` | — |
| `upsertReturning` | `NotificationSettingsDataSourceImpl.upsert(settings)` | insert via DAO entity directly |
| `upsert(where = …)` | — | split into `insert`/`update` datasource methods and let the operation choose (`NotificationTargetDataSourceImpl` + `UpdateTargetInformation`) |

For read methods whose writes use an unsupported upsert, insert test data directly via the DAO entity (e.g. `NotificationSettingsEntity.new(id) { ... }`).

Plain `upsert {}` (no `where` parameter) **is** supported by H2 and resolves conflicts via `uniqueIndex` columns — use it freely in datasource tests. Passing `where` to it throws `UnsupportedByDialectException`. Split a conditional upsert into separate `insert`/`update` datasource methods — the guard lives in the `update {}` where clause and returns whether a row matched — and let the operation decide, inside its `transactionManager.transaction { }`, whether to insert.

### Asserting unique-constraint violations

`runUnitTest` cannot be nested inside `assertThrows` (its body is `suspend`, and the outer `TestHolder` context would be lost). Assert constraint failures with `runCatching` instead:
```kotlin
whenn()
val result = runCatching { suspendTransaction { fixture.sut.createX(duplicate) } }

then()
assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
```
`dbQuery { }` maps the driver exception to `com.bookk.core.domain.entity.Error.UniqueConstraintFailed`, which operations turn into a domain error via `.onConstraintFailure { }`.

A DAO entity **update** (`findByIdAndUpdate`, setting a property on a found entity) is only flushed at the next statement or at commit, after `dbQuery { }` has returned, so its constraint violation is the raw driver exception, not `Error.UniqueConstraintFailed`. In production `ExposedTransactionManager` maps it with `toDomain()`, so `.onConstraintFailure { }` on the operation still works; in an H2 datasource test assert `result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed` (`UserDataSourceImplTest`).

A DAO insert (`XEntity.new { }`) is flushed as a JDBC **batch**, so the driver reports a duplicate as `java.sql.BatchUpdateException`, not `SQLIntegrityConstraintViolationException` — verified on `mariadb-java-client` 3.4.1 (SQLState `23000`, and its cause is another `BatchUpdateException`) and on H2 (`23505`). `core/data/map/Mapper.kt`'s `toDomain()` therefore classifies by SQLState class `23` as well as by exception type; narrowing it back to an `instanceof` check turns every duplicate DAO insert into `Error.DatabaseError` (500) and silently disables `.onConstraintFailure { }` (`DataSourceConstraintMappingTest` covers both the single-row and batch paths). A "does it already exist?" check followed by an insert is never enough on its own: back it with a unique index and map the race with `.onConstraintFailure { }` to the same domain error the check throws (`JoinBusinessImpl` + `employee_business_id_user_id_unique`).

### Batch-update / batch-cancel patterns

`markCompleted(before: Instant)` and `cancelOutdated(before: Instant)` update rows where `dateEnd < before`. Test the happy path by creating entities at `Instant.fromEpochMilliseconds(0)` (epoch → well in the past) and calling with `Clock.System.now()`. Test the boundary by creating entities at `Clock.System.now() + 24.hours` and verifying they are NOT affected.

### Cleanup methods (deleteDayOffsInThePast)

Insert `DayOffRange(LocalDate(2020,1,1), LocalDate(2020,1,2))` for a past day-off and `DayOffRange(LocalDate(2099,12,30), LocalDate(2099,12,31))` for a future one. After calling `deleteDayOffsInThePast()` re-read via `sut.get(businessId)` and assert counts.

### Never use `advanceUntilIdle()` to drive work running on `backgroundScope` — use `advanceTimeBy` + `runCurrent`

This bit twice before the real cause was found, so: **`TestScope.advanceUntilIdle()` deliberately does not fully drain `backgroundScope`.** Straight from the `kotlinx-coroutines-test` KDoc on `TestScope.backgroundScope` — "The coroutines in this scope are run as usual when using `advanceTimeBy` and `runCurrent`. `advanceUntilIdle`, on the other hand, will stop advancing the virtual time once only the coroutines in this scope are left unprocessed." This is intentional (it's what lets a `backgroundScope` job that loops forever not hang `advanceUntilIdle()`), not a bug — but every `EmbeddedEventConsumer` test here runs the consumer via `consumer.start(backgroundScope)`, so `advanceUntilIdle()` is the wrong tool for **any** of them, not just multi-round DLT retry chains: it previously looked like it worked (a plain single-hop `send()` → `advanceUntilIdle()` → assert delivered) purely by timing coincidence, and stopped working the moment something upstream changed (see `EmbeddedEventStreamingTest`, which predates this whole feature and still hit it).

The fix, per that same KDoc: drive time with `runCurrent()` (runs whatever is due at the current virtual instant — nothing more) and `advanceTimeBy(duration)` (moves the clock forward, running scheduled tasks in the meantime) instead. Two things to know about these:
- `advanceTimeBy(duration)` does **not** run tasks scheduled for exactly `currentTime + duration` — always follow it with a `runCurrent()` to catch anything sitting exactly on that boundary.
- `runCurrent()` alone will not run a task scheduled via `delay()` for a future virtual time — only `advanceTimeBy`/`advanceUntilIdle` actually move the clock, so a chain with real backoff needs the clock advanced past each `delay()`, not just `runCurrent()` in a loop.

Pattern used across `EmbeddedDltRetryTest`/`EmbeddedEventStreamingTest`: for a delay-free single hop, `runCurrent()` alone is enough. For a chain with backoff (DLT retries), advance a single generous virtual duration (see `advanceThroughRetries()`: `runCurrent(); advanceTimeBy(1.hours); runCurrent()`) — virtual time is free, so there's no cost to advancing far more than the scenario actually needs, and it reliably sweeps through however many delay-then-reschedule rounds occur without having to compute exact cumulative backoff by hand.

### `runUnitTest`'s timeout is real time, not virtual

`runUnitTest` passes its 30-second budget as `runTest(context, timeout = 30.seconds) { ... }` (`core/src/testFixtures/.../Test.kt`), not a manual `withTimeout()` nested inside the test body. `runTest`'s own `timeout` parameter is a real-wall-clock watchdog against a hung test (it runs on the outer `runBlocking` dispatcher, outside the `TestCoroutineScheduler`) — it does not count virtual time advanced via `advanceTimeBy()`/`advanceUntilIdle()`. So a DLT retry test can freely use the production `DltRetry.DEFAULT_MAX_ATTEMPTS` and uncapped `backoffMillis` even though cumulative simulated backoff reaches many seconds or more — advancing that much virtual time costs no measurable real time.

Because the budget is real time, it also has to cover a test JVM's cold start, which the first test in each module pays: loading MockK and ByteBuddy, creating the first H2 database and starting Exposed, Ktor's test engine, Koin, the ProtoBuf serializers. Locally that is 0.75s to 4.2s (`CommunicationDataSourceImplTest`). On a GitHub runner shared by parallel Gradle test JVMs and the Testcontainers suites it went past the old 5-second budget, and CI failed a different module's first test each run with `UncompletedCoroutinesError` ("the test body did not run to completion") while every test passed locally. A Linux container capped at `--cpus=0.25` reproduces it. That is why the budget is 30 seconds, and `routeTest` runs on `runUnitTest` like everything else. Don't lower it to catch slow tests: the first test's time measures the JVM, not the test. For the same reason, keep real cryptography out of unit tests. RSA-2048 key generation is a random prime search with a long tail. `GetActiveSigningKeyImpl` and `RotateSigningKeysImpl` take a `SigningKeyPairGenerator` (bound in `signingModule` to `RsaSigningKeyFactory::generate`, scoped because every service scope loads the module), and their tests stub it. A test that only passes keys through uses `SigningKey.stub()`.
