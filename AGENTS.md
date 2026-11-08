# bookk-server — Agent Playbook

Modular monolith split into Ktor microservices. Kotlin + Ktor (CIO) + Koin DI + Exposed ORM + kotlinx ProtoBuf serialization + MockK/JUnit5 tests. Follow the recipes below verbatim; they mirror the real code (reference implementation: `service/appointments`, the newest service).

## Module map

```
core/                    shared infra: domain Result/Error types, service (Ktor server, auth, respondWith), data (Exposed, event streaming, cache)
library/                 money, idempotency, permissions, validation
service/<svc>/
  domain/api/            operation interfaces + entities + <Svc>ErrorCodes   (package com.bookk.<svc>.domain.api.{operation,entity})
  domain/impl/           <Operation>Impl + di/DI.kt + tests                  (package com.bookk.<svc>.domain.impl.{operation,di})
  data/source/           datasource INTERFACES                               (package com.bookk.<svc>.domain.datasource)
  data/                  datasource impls, orm tables/entities, migration, di
  microservice/          route/<Svc>Routing.kt (typed Resources), route/api/*Route.kt, <Svc>Microservice.kt main, route tests
  client/                cross-service events/API
```

New gradle modules must be registered in `settings.gradle.kts` (one `include` per submodule, grouped per service). Convention plugins (`libs.plugins.bookk.microservice`, `bookk.domain.impl`, `bookk.domain.api`, `bookk.data`, …) already add Ktor/Koin/MockK/kotlin-test deps and JUnit platform — do not re-add them, and declare each dependency once per build file (details in `docs/agents/build-and-ci.md`).

## Read before you touch…

The details of each area live in `docs/agents/`. Open the matching file before working in that area; every rule there is as binding as one here.

| Area | File |
|---|---|
| Gradle modules and dependencies, ktlint/Spotless, opt-ins, CI and image publishing, pinned images, Testcontainers version | `docs/agents/build-and-ci.md` |
| Migrations, ER diagrams, NocoDB tables, operation activity diagrams | `docs/agents/migrations-and-diagrams.md` |
| Monolith wiring, `createDatabase`/HikariCP/TLS/retry, JWKS and token audiences, internal HTTP clients, logging and metrics, `RedisCacheClient` | `docs/agents/infrastructure.md` |
| `runSuspendCatching`, `runCatchingUnlessCancelled`, `runCleaningUpOnFailure`, `ticks` | `docs/agents/coroutines.md` |
| Transactional outbox, event consumers, idempotent delivery, DLT, exhausted events | `docs/agents/events.md` |
| Transaction retries, lock-first ordering, gap locks, double booking, MariaDB locking tests | `docs/agents/transactions-and-locking.md` |
| Datasources and DAO entities (writes on the entity, referrers, get-or-create, `EntityID`) | `docs/agents/data-layer.md` |
| Passkey action confirmation, email codes, single-use tokens, data from other services, Android signing certificates | `docs/agents/security.md` |
| Business currency, client uniqueness/takeover/merges, device identity | `docs/agents/domain-rules.md` |
| Data export, legal documents, `client_id` vs users, account and client erasure, dormancy, retention, erasure records | `docs/agents/personal-data.md` |
| OpenAPI plugin details, Swagger tests | `docs/agents/openapi.md` |
| H2 datasource tests, scheduled job tests, virtual time, `runUnitTest` timeout | `docs/agents/testing-advanced.md` |

## Always

**Ignore all git operations.** Managing Git is the responsibility of the developer, do not automatically commit, push or merge
**Bump the version after every code change.** The single project version lives in the root `build.gradle.kts` (`version = "X.Y.Z"`, inherited by every module via `rootProject.version`). After changing code, bump it per semantic versioning according to the kind of change: **MAJOR** for a backwards-incompatible change (removed/renamed route, field or `@ProtoNumber`; changed wire type; changed meaning of an existing field or error code; a migration that breaks older service instances), **MINOR** for a backwards-compatible feature (new route, operation, event, entity field or error case, new allowed behavior or new rejection rule), **PATCH** for a backwards-compatible fix, refactor or internal change with no API/behavior addition. A higher part resets the lower ones (`0.3.5` → `0.4.0`, `1.2.3` → `2.0.0`). **While the version is `0.x`, a backwards-incompatible change bumps MINOR, not MAJOR** — removing or renaming a route, field or `@ProtoNumber` is allowed freely and counts the same as a new feature (`0.12.0` → `0.13.0`). A release does not move the version to `1.0.0`: the first public release shipped as `0.15.7` (`RELEASE_NOTES.md`), and the project moves to `1.0.0` only when the developer decides to; the MAJOR rule applies from then on. Measure against the last released version, the newest `monolith/<version>` tag (`git tag -l 'monolith/*' --sort=-v:refname | head -1`), not the last commit or the working tree: everything since that release gets exactly one bump, at the level of its most significant change. If the current version is already above the release at that level or higher, leave it, even when the bump was committed earlier on the branch; if it is above it at a lower level (e.g. a patch bump, then a feature is added), raise it to the correct level from the released version. Docs-only or test-only changes do not bump the version. **Keep `RELEASE_NOTES.md` current in the same change.** Its top entry is the unreleased version (the one in `build.gradle.kts`, above the newest `monolith/<version>` tag) and grows with every change since that release: what changed for clients and operators, new error codes, migrations and anything that can stop a deploy, and the upgrade steps. **Keep it compact.** Each item is a single short sentence, at most two: what changed, plus what a client or operator has to do about it. Name the route, error code, event or setting that changed. Leave out the root cause, the history of the bug, class and test names, and how the fix works; that belongs in the code, `docs/` and this file. The summary line under the version heading is one sentence, not a list of every item. When the version bump raises its level (a patch entry, then a feature), rename the entry to the new version and rewrite its summary line; never start a second unreleased entry.
**Formatting:** run `./gradlew spotlessApply` after changing code; CI runs `spotlessCheck` and fails on any unformatted file. Never silence a ktlint rule with `@Suppress("ktlint:…")` in source.
**Never write `@OptIn` in source;** opt-ins live only in `build-src/.../convention/KotlinConvention.kt`.
**Write database migrations by hand.** This is Claude's job. Append to the service's unreleased script (one migration per service per release), make every statement safe to run twice, and run Flyway against a real MariaDB at `127.0.0.1:3308` before calling it done. Read `docs/agents/migrations-and-diagrams.md` first.
**Keep `docs/database/` (ER diagrams) and `docs/operations/` (activity diagrams) current** in the same change as an ORM table change, a new or changed mutating route, or an event handler (`docs/agents/migrations-and-diagrams.md`).
**Deployment lives in `deployment/AGENTS.md`** (prod topology, database and cache networking, Swarm restart policy, memory sizing, monitoring, Ansible stacks, VPN and nginx, the dev environment and rollout scripts), loaded automatically through `deployment/CLAUDE.md` when you read a file under `deployment/`. Prod runs the monolith, not the five microservices, and has no Kafka. Read that file before a code change that prod depends on, even outside `deployment/`: database connection, TLS, retry or pool settings (`createDatabase`, `APPLICATION_DB_*`), cache TTLs (Valkey is sized from `PROCESSED_TTL` and the idempotency response TTL), metric names and labels (alerts and dashboards query them), the log format (Alloy reads the logback level), environment variables, ports, and service hostnames.
**The monolith (`MonolithServer.kt`) uses embedded events, never Kafka.** A new service gets an `xEmbeddedModule()` registered in `MonolithServer.diModules()` (`docs/agents/infrastructure.md`).

## Core conventions (apply everywhere)

- **Never write comments.** No `//` comments, no KDoc, no explanatory blocks — in production code or tests. Express intent through names and structure instead: rename the function, extract the condition into a named value, split the method. If something seems to need a comment, that is a signal the code should be clearer. The only exceptions are the route KDoc the openApi plugin parses (`Summary:`, `Body:`, `Response:`, …) and comments that already exist in the file — never delete or reword someone else's comment.
- Operations return `Result<T>`; impls **throw** errors inside `transactionManager.transaction { }`, which catches and converts to `Result.failure`. Where a transaction's failure should just propagate out of an enclosing `runSuspendCatching { }` (a job, a multi-step operation), write `transactionManager.transactionOrThrow { }` (both isolation overloads, `core/domain/datasource`), never `transaction { }.getOrThrow()`; its return label is `return@transactionOrThrow`.
- Domain errors: nested `sealed interface Error` in the operation interface; each case is a `class` extending `BusinessError(statusCode, code, message)` (classes, NOT data objects). Assert with `is`, never equality. Exception: appointment status-transition errors are shared, not per operation — `AppointmentStatusError` (`AlreadyCancelled`, `NotStarted`, `EditWindowClosed`, next to the `Appointment` entity) is thrown by the entity's own guards `Appointment.requireEditable()` / `requireCompletable(now)` / `requireNoShowMarkable(now)`. An operation that changes an appointment's status calls the matching guard on the row it read with `getForUpdate` instead of re-declaring these cases in its own `Error`. Appointment requests follow the same pattern: `AppointmentRequest.requirePending()` throws the shared `AppointmentRequestStatusError` (`AlreadyApproved`, `AlreadyDeclined`), used by both decline and approval (a sealed interface's subclasses must live in its own package, so the entity cannot throw an operation's nested error).
- Error codes live in `domain/api/.../<Svc>ErrorCodes` as `BASE + n`. Blocks: auth=0, user=100000, business=200000, appointments=300000, notifications=400000. Next service takes the next 100000 block. Service-independent codes live in `core/domain/.../CoreErrorCodes` (block 900000, e.g. `TRANSACTION_CONFLICT`). A library's codes live in its own `<Lib>ErrorCodes` in its api module (`library.email.EmailErrorCodes`), never in `CoreErrorCodes`, from its own 10000 block after core's: email = 910000, the next library 920000. Codes released before that keep their values: `ExportErrorCodes.DATA_EXPORT_TOO_FREQUENT` is 900002 inside core's block, so core's next code is `BASE + 3`, and `PermissionErrorCodes.EMPLOYEE_ACCESS_SUSPENDED` is business's 200034 on purpose.
- Generic infrastructure errors: `com.bookk.core.domain.entity.Error` (`NotFound`, `OperationNotAllowed`, …).
- `call.respondWith(result)` (core/service) maps: success Unit→204, success T→200, `BusinessError`→its statusCode + `SimpleServerError(errorCode, message)`, `Error.NotFound`/`Error.OperationNotAllowed`→**404** (intentional: permission failures do NOT return 403), `Error.TransactionConflict`→**409** + `SimpleServerError(CoreErrorCodes.TRANSACTION_CONFLICT, …)`, anything else→500 (logged).
- Permissions: fine-grained per-resource grants (`library.permissions.ResourcePermission(view, update, delete)`, three independent booleans with no defaults — always pass all three, no ranking between them) — `businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.CLIENTS).assert(PermissionAction.UPDATE)` (business) or `appointmentPermissionDataSource.getPermission(userId, businessId).assert(PermissionAction.VIEW)` (appointments, single implicit resource) — throws `Error.OperationNotAllowed`. A caller acting on their own assigned resource (e.g. an employee's own appointment) can pass an `update`/`delete` check on a `view`-only grant via `.assertOrSelf(action, actorId, assigneeId)`. An employee's suspension (`Employee.suspendedAt`) is enforced inside `BusinessPermissionDataSource.getPermission`, which throws `library.permissions.EmployeeAccessSuspended` (403, `BUSINESS_EMPLOYEE_ACCESS_SUSPENDED`) for a suspended caller (`getViewer`/`getViewers` just report `NONE` permissions and a null `employeeId`); appointments mirrors it via the `suspended` flag on `EmployeePermissionsChanged` and `appointment_permission_grants.suspended`, throwing the same `library.permissions.EmployeeAccessSuspended`, so a new permission-gated operation gets it for free — list that 403 in its route KDoc — only paths with no permission gate (client booking via `GetAppointmentBookingContext`) must check `employee.isSuspended` themselves, and anything publishing `EmployeePermissionsChanged` must set `suspended = employee.isSuspended` alongside the stored `permissions`. See `docs/object-permissions.md` for the full model, including how a `Business`/`AppointmentSettings` entity gets the requesting user's grants attached (`Business.viewedBy(businessPermissionDataSource.getViewer(...))` / `.copy(permissions = ...)`, computed per request, never persisted on the row). A list operation resolves its caller-specific fields with one batched lookup over every id (`getViewers(userId, businessIds)`), never one datasource call per row.
- Name/email/phone/currency format checks: `library.validation.{NameValidator,EmailValidator,PhoneValidator,CurrencyValidator}.isValid(value, ...)` (library/validation, plain `bookk.domain.api` module, no domain dependency — only joda-money, for `CurrencyValidator`) — pure `Boolean` predicates, each with a default `maxLength`/`minLength` you can override per call site; the caller still owns throwing its own `*ValidationError`. Reuse these instead of hand-rolling a regex/length check in a new operation.
- Wire format is ProtoBuf (`application/x-protobuf`) for all bodies/responses. **A nullable collection (`List<T>?`, `Map<K, V>?`) cannot be serialized when null** — kotlinx throws `'null' is not supported as the value of collection types in ProtoBuf`. For an optional group of fields in a partial-update DTO, wrap them in a nullable `@Serializable` holder class (a nullable message is fine) instead of making each list nullable — see `BusinessUpdateModel.schedule: Schedule?`. **A nullable property must not also have a default** — the serializer runs with `encodeDefaults = true`, and encoding a defaulted null throws `'null' is not supported for optional properties in ProtoBuf` as soon as a caller omits it. Give every nullable field on a partial-update DTO no default at all and pass them explicitly (`BusinessUpdateModel`, `UserEditModel`). **Never use a `Map` keyed by an enum as a request body field** (e.g. `Map<BusinessResource, ResourcePermission>`): the openApi plugin renders it as bare `additionalProperties` with no key list, and on the ProtoBuf wire the keys are unannotated enum ordinals, so the valid keys are documented nowhere. Give the DTO one nullable field per enum value with its own `@ProtoNumber` and convert to a map in the DTO (`EmployeePermissionsRequest.grants()`).
- Entities: `@Serializable data class` in `domain/api/.../entity` with a `companion object { fun stub(...) }` factory (defaulted params, `Uuid.random()`, `Instant.fromEpochMilliseconds(0)`) — add `stub()` to every new entity; tests rely on it.
- **Every ProtoBuf wire type carries an explicit `@ProtoNumber` on every serialized field** — request/response DTOs, entities, events, and enum entries (`@ProtoNumber(n) ENTRY`, which replaces the implicit ordinal). Without one, kotlinx numbers a field by declaration order (index + 1; ordinal for enums), so inserting or reordering silently renumbers the wire. That includes an event's body property `override val topic: String = TOPIC`, which is serialized after every constructor parameter — annotate it on the line above (`@ProtoNumber(n)\n override val topic`). A new field takes the next unused number with a default; never reuse or change an existing one. JSON-only types (`Jwk`/`JwkSet`, the idempotency cache records) are exempt.
- **Never throw a `BusinessError` inside `dbQuery { }`.** `DataSource.mapExceptions` passes only core `com.bookk.core.domain.entity.Error` through; anything else, `BusinessError` included, becomes `Error.UnknownError` and a 500. A datasource that must fail with a domain error returns the raw result from `dbQuery` and throws after it (`BusinessPermissionDataSourceImpl.getPermission`). `ExposedTransactionManager` does pass `BusinessError` through.
- **A write addressed by a child id must be scoped to the business the permission check ran against.** The permission check passes on the `businessId` the caller supplies (path or body), so a datasource update/delete that matches only `WHERE id = ?` lets anyone with a grant in their own business reach another business's row by id. Put `business_id = ?` in the same `WHERE` (`ServiceDataSourceImpl.editService`, `deleteService`, `deleteServiceGroup`), and never write `businessId` from the request in an update's `set` block. Resolve a referenced child the same way, with a scoped read, and store and return what the server found, not the object the client sent (`getServiceGroup` in `CreateService`/`UpdateService`).
- **`transactionManager.transaction { }` may run its block up to 3 times** (write-conflict retry), so a block must be safe to re-run: no push, email or other non-database side effect inside it. Every datasource call runs inside one, event handlers included (`docs/agents/transactions-and-locking.md`).
- **Take a row lock (`getForUpdate`, `lockBusiness`) as the transaction's first statement**, before any plain read, the permission lookup included (`docs/agents/transactions-and-locking.md`).
- **`eventProducer.send` must run inside `transactionManager.transaction { }`**: it writes to the service's `outbox_event` table (`docs/agents/events.md`).
- **Use `runSuspendCatching { }`, never `runCatching { }`, around any block that suspends** (`docs/agents/coroutines.md`).
- **Never accept another service's identity or pricing data as free text from the client;** resolve it server-side by id (`docs/agents/security.md`).
- **Claim a single-use token with one atomic `CacheClient.setIfAbsent`**, never a `get` check followed by a `set` (`docs/agents/security.md`).
- **Every amount inside one business is in the business currency** (`docs/agents/domain-rules.md`).
- **Every write that books a slot takes `AppointmentSettingsDataSource.getForUpdate(businessId)` before its overlap check;** never a unique index on slot columns (`docs/agents/transactions-and-locking.md`).

## Recipe: new business operation

Files to touch (example names from appointments):

1. `domain/api/.../operation/DoThing.kt` — interface + errors:
```kotlin
interface DoThing {
    suspend operator fun invoke(userId: Uuid, ...): Result<Thing>

    sealed interface Error {
        class ThingExists : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = SvcErrorCodes.THING_EXISTS,
            message = "Thing already exists",
        ), Error
    }
}
```
2. Add the code constant to `<Svc>ErrorCodes`.
3. `domain/impl/.../operation/DoThingImpl.kt`:
```kotlin
internal class DoThingImpl(
    private val thingDataSource: ThingDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer, // only if events sent
) : DoThing {
    override suspend fun invoke(userId: Uuid, ...): Result<Thing> = transactionManager.transaction {
        val thing = thingDataSource.get(id) ?: throw Error.NotFound()
        businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.THINGS).assert(PermissionAction.UPDATE)
        if (conflict) throw DoThing.Error.ThingExists()
        thingDataSource.create(...) // also { eventProducer.send(SvcEvent.X(...)) } if needed
    }
}
```
4. Register in `domain/impl/.../di/DI.kt`: `factoryOf(::DoThingImpl) bind DoThing::class`.
5. If a new datasource method is needed: add to the interface in `data/source/.../datasource/`, implement in `data/.../datasource/<X>DataSourceImpl.kt` (`internal class ... : DataSource(), XDataSource`, queries wrapped in `dbQuery { }`, Exposed v1 DSL, `Uuid` for ids). New datasources are registered in `data/.../di/`: `singleOf(::XDataSourceImpl) bind XDataSource::class`. New tables go in `data/.../orm/{table,entity}` and need a hand-written migration script (see "Write database migrations by hand" above).
   Datasource and DAO entity patterns (writes on the entity, never `flush()`/`refresh()`, the first read is the locking one, referrers, race-safe get-or-create): `docs/agents/data-layer.md`.
6. Write the impl unit test (see Testing) — every `Error` case + success + event publication if any.

## Recipe: new Ktor route

1. Add a typed resource to `route/<Svc>Routing.kt` (nested `@Resource` classes with `val parent: X = X()` chain):
```kotlin
@Resource("/{id}/cancel")
class Cancel(val parent: Appointment = Appointment(), val id: Uuid)
```
2. Add/extend a `fun Routing.xyz()` in `route/api/<Thing>Route.kt`. Protected routes go inside `authenticate { }`; inject operations lazily inside the handler; request-only DTOs are `@Serializable internal class` at the top of the route file:
```kotlin
fun Routing.thing() {
    authenticate {
        /**
         * Summary: Create thing
         * Description: Create new thing from request
         * Tag: thing
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.svc.microservice.route.api.ThingRequest]
         * Response: 200 application/x-protobuf [com.bookk.svc.domain.api.entity.Thing] Created thing
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Create thing errors<br>THING_EXISTS (300010) Thing already exists
         */
        post<Api.Thing> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<ThingRequest>()
            val doThing by application.inject<DoThing>()

            call.respondWith(doThing(userId = principal.userId, ...))
        }
    }
}
```
3. KDoc OpenAPI rules (the ktor openApi plugin parses these): a plain-text summary preamble, optional `Description:`, `Tag:`, `Security: jwt` only when inside `authenticate {}`, **`Body:` (NEVER `RequestBody:`)**, one `Response:` line per status. Fully qualified type names in brackets. Every case of the operation's `sealed interface Error` must appear under the 422 response with `NAME (<n>) message`. 204 responses: `Response: 204 application/x-protobuf <description>` (no type). **Never use a colon (`:`) anywhere in the KDoc text after the field prefix** (e.g. `NAME (<n>) message`, not `NAME (<n>): message`) — the openApi plugin parses on `:` and an extra one breaks the field. **NEVER put a generic type in a `Response:` KDoc line** (e.g. `[kotlin.collections.List<Foo>]` silently produces no schema — the compiler plugin's `TypeReference$Link$Reference.asIrType()` passes the raw string including `<…>` to `FqName()` then `ClassId.topLevel()`, which finds no class and returns null). For simple list responses, use `[fully.qualified.Element]+` in the `Response:` line, for example `Response: 200 application/protobuf [com.bookk.svc.domain.api.entity.Thing]+ List of things`. Reserve `.describe { }` for schemas the comment parser cannot resolve, such as nested classes or complex generics. A module using the runtime DSL declares `implementation(libs.ktor.routing.openapi)` itself; from Ktor 3.6 it is no longer available through Ktor's own dependencies.

Ktor version constraints and list responses in KDoc: `docs/agents/openapi.md`.

4. When a path id duplicates a body id, validate: `if (it.id != body.id) call.respond(HttpStatusCode.BadRequest, "Invalid request") else ...`.
5. Register the new route fn in `route/<Svc>Route.kt` aggregator (`fun Routing.<svc>Route()`); the aggregator is already wired in `<Svc>Microservice.kt`.
6. `AppPrincipal` fields: `authId`, `userId`, `deviceId` (all `Uuid`).
   To give an existing body-less route an optional body without breaking older clients, read it with `call.receiveNullable<XRequest>()` and keep every `XRequest` field nullable with no default — an empty request decodes to `null` (verified by `CompleteAppointmentTest`, which still posts with no body).
7. **For `get`/`post`/`delete`/`put`/`patch` matching a typed `@Resource`, import from `io.ktor.server.resources.*`, never `io.ktor.server.routing.*`.** The two packages both export a same-named function taking a generic type parameter, so the wrong import still compiles: `io.ktor.server.routing.patch<R>(body: suspend RoutingContext.(R) -> Unit)` treats `R` as a **request-body type to deserialize** (`call.receive<R>()`) and registers a bare `method(HttpMethod.Patch)` node with no path — it silently ignores the `@Resource` class entirely instead of routing by it. A route written as `patch<Api.Thing.Id> { ... }` with that import compiles clean but never matches its real URL, so every request 404s with no error anywhere (found via `service/business/microservice/route/api/Client.kt`, which had `import io.ktor.server.routing.patch` where every sibling handler in the same file correctly imported from `io.ktor.server.resources.*`).

## Testing

Run: `./gradlew :service:<svc>:microservice:test` / `:service:<svc>:domain:impl:test` (append `--tests "com.bookk...ClassName"` to filter).

Hard rules (enforced by fixtures or review):
- NEVER `runBlocking`. Operation tests: `runUnitTest { }`; route tests: `routeTest { }` (both from core fixtures).
- `given()` / `whenn()` / `then()` markers are **required in every test** — `runUnitTest` asserts at runtime that all three were called; a test without them fails.
- Fresh SUT and fresh mocks per test — no sharing across tests, no class-level mocks.
- DO NOT EDIT `core/src/testFixtures/kotlin/com/bookk/core/test/Test.kt`.
- Test names: backticked sentences, e.g. `` fun `should return failure when request overlaps with existing appointment`() ``.
- Use entity `stub()` factories instead of hand-built instances; pass only the fields the test depends on. Provide real entity instances to mocks (ProtoBuf serialization NPEs otherwise). **Never write a local test-file helper function (`private fun createTestX()`, `makeX()`, …) that re-lists an entity's fields to construct it** — that duplicates the field list the entity's own `stub()` exists to own. If the entity has no `stub()` yet, add one to its companion (see the Entities rule above) and call it directly at each test call site instead. The one exception is a partial-update DTO (`BusinessUpdateModel`, `ClientUpdateModel`, …) — those are request shapes, not domain entities, so they don't get a `stub()`; a local private helper (conventionally named `updateModel(...)`, defaulting every field to `null`) is the established pattern for building them in tests, e.g. `UpdateBusinessImplTest.updateModel()`.
- JUnit assertions (`org.junit.jupiter.api.Assertions`). Assert error types with `is`: `assertTrue(result.exceptionOrNull() is DoThing.Error.ThingExists)`.
- If the operation sends events: include a test with `coVerify(exactly = 1) { eventProducer.send(any(SvcEvent.X::class), any()) }`.

### Operation (domain/impl) test template

Using testing rules from AGENTS.md cover all ViewModels, Use cases(operations) and Data sources with unit tests. Afrer covering each feature (appointments, business etc.) Do a code review of your work and fix the issues if encountered. Make sure tests are fast, repeatable. Create utility functions or testFixtures in the core module for tools that are used across each tests. Do not share the state between unit tests, use private class SutFixture {
val thingArgParameter = mockk<SomeArgClass>()
val sut = ClassUnderTest(thingArgParameter)
} pattern to init SUT to make sure new instance created for every unit test. Use mockk kotlinx.test and junit5 primarily to write unit test. After the work is done if any new useful knowledge acquired, insert it in AGENTS.md or the matching `docs/agents/` file.

```kotlin
internal class DoThingImplTest {

    private class SutFixture {
        val thingDataSource = mockk<ThingDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DoThingImpl(thingDataSource, businessPermissionDataSource, transactionManager)
    }

    @Test
    fun `should create thing successfully`() = runUnitTest {
        given()
        val userId = Uuid.random()
        val thing = Thing.stub(userId = userId)
        val fixture = SutFixture()
        with(fixture) {
            coEvery { businessPermissionDataSource.getPermission(userId, thing.businessId, BusinessResource.THINGS) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { thingDataSource.create(any()) } returns thing
            transactionManager.mockTransaction() // testFixtures(projects.core.domain.datasource)
        }

        whenn()
        val result = fixture.sut.invoke(userId, thing)

        then()
        assertTrue(result.isSuccess)
        assertEquals(thing, result.getOrNull())
    }
}
```
Permission-denied case: stub `getPermission` to return `ResourcePermission(view = true, update = false, delete = false)` (or `ResourcePermission.NONE` for "no grant at all"), assert `result.exceptionOrNull() is Error.OperationNotAllowed`.

### Route (microservice) test template

```kotlin
internal class CreateThingTest {

    @Test
    fun `should create thing successfully`() = routeTest {
        given()
        val useCase: DoThing = mockk()
        val userId = Uuid.random()
        val thing = Thing.stub(userId = userId)
        coEvery { useCase.invoke(userId, any()) } returns Result.success(thing)

        setupApplication(
            extension = {
                install(Authentication) {
                    provider {
                        authenticate { context ->
                            context.principal(AppPrincipal(Uuid.random(), userId, Uuid.random()))
                        }
                    }
                }
            },
            diModule = module { single { useCase } },
            routeUnderTest = { thing() }
        )

        whenn()
        val client = createTestClient()
        val response = client.post(SvcRouting.Api.Thing()) { setBody(ThingRequest(...)) }

        then()
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```
- Domain-error case: `coEvery { ... } returns Result.failure(DoThing.Error.ThingExists())`, then assert status 422 and `response.body<SimpleServerError>().errorCode == SvcErrorCodes.THING_EXISTS`.
- Unauthorized case: `extension = { install(Authentication) { bearer { authenticate { null } } } }`, assert 401. Omit `Security` mocking nothing else.
- Permission-denied surfaces as **404** through `respondWith` — assert 404, not 403.
- Requests use typed resources (`client.post(SvcRouting.Api.Thing())`), never string paths. `createTestClient()` already sets ProtoBuf content type.
- Fixtures come from: `testImplementation(testFixtures(projects.core))` (given/whenn/then, runUnitTest), `testFixtures(projects.core.service)` (routeTest, setupApplication, createTestClient), `testFixtures(projects.core.domain.datasource)` (mockTransaction). Add `libs.joda.money` if entities use Money. Check the module's `build.gradle.kts` before adding — appointments modules already have these.

### Mandatory coverage (contract-first)

Before writing code, enumerate and track as a checklist:
1. Happy path.
2. EVERY case of the operation's `sealed interface Error` — at BOTH levels: impl test (exception type) and route test (HTTP status + `SimpleServerError.errorCode`).
3. Auth: 401 unauthenticated; permission-denied (impl: `Error.OperationNotAllowed`; route: 404).
4. Event publication, if the operation sends events.

Finish by emitting a Final Coverage Report mapping every checklist item to its test:
```
### Final Coverage Report
- [x] ThingExists: impl exception test + route 422/THING_EXISTS test
- [x] Unauthorized: route 401 test
```
Report any gaps explicitly — never silently skip a case.

---

### Development Workflow: Test-Driven Development

Always implement features using strict TDD. For every requested feature or change:

1. **Red** — Write a failing test first that captures the expected behavior. Do not write any implementation code before the test exists.
2. **Green** — Write the minimum implementation needed to make the test pass.
3. **Refactor** — Clean up the implementation and tests while keeping all tests green.

Rules:
- Never write production code without a failing test that requires it.
- Run the test suite after each step and confirm the expected pass/fail state before proceeding.
- Keep tests small and focused; one behavior per test.
- If a requirement is ambiguous, write the test that encodes your assumption and state it explicitly.

---

Datasource (H2) tests, scheduled job tests and coroutine timing: `docs/agents/testing-advanced.md`.
