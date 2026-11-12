# Datasources and DAO entities

Read before adding or changing a datasource, a DAO entity or an ORM table. Moved out of `AGENTS.md`; every rule here is as binding as one there.

### Writes live on the entity, not in the datasource

Mapping a domain model onto rows belongs in the DAO entity's companion, so a datasource method stays a one-liner and the field list has exactly one home. Reference: `AppointmentBusinessEntity`, `BusinessEntity`, `AppointmentEntity`.

```kotlin
internal class ThingEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var name by ThingTable.name

    fun domain(): Thing = Thing(id = id.value, name = name)

    private fun replaceChildren(children: List<Child>) {
        val entityId = id.value // `id` inside a table lambda resolves to the table's column, not the entity
        ChildTable.deleteWhere { ChildTable.thingId eq entityId }
        ChildTable.batchInsert(children) { this[ChildTable.thingId] = entityId /* … */ }
    }

    companion object : DecoratorUUIDEntityClass<ThingEntity>(ThingTable) {
        fun new(model: Thing): ThingEntity = new { name = model.name }.apply { replaceChildren(model.children) }

        fun findByIdAndUpdate(model: ThingUpdate) = findByIdAndUpdate(model.id) {
            model.name?.let { name -> it.name = name }
            it.updatedAt = Clock.System.now()
        }
    }
}
```
```kotlin
override suspend fun create(model: Thing): Thing = dbQuery { ThingEntity.new(model).domain() }
override suspend fun update(model: ThingUpdate): Thing = dbQuery {
    (ThingEntity.findByIdAndUpdate(model) ?: throw Error.NotFound()).domain()
}
```

Rules that fall out of this:
- **Never call `flush()` or `refresh()`.** Exposed flushes its entity cache before executing any statement, so a staged insert/update is always written before the next query or DSL statement observes it. Every such call added during this codebase's history turned out to be dead — `refresh()` is worse than dead, it re-reads the row and discards pending writes, which then needs a `flush()` before it to compensate.
- Business-rule guards (status transitions, "has the appointment started", …) belong in the **operation**, not the datasource: the operation's **first** read of the row is a locking one (`getForUpdate`, `SELECT … FOR UPDATE`), it checks and throws its own domain errors, then calls an unconditional datasource write (`markCompletedByUser`, `markNoShow`). The lock is held for the rest of `transactionManager.transaction { }`, so no concurrent write slips in between. The locking read must come first: Exposed's `EntityClass.wrap` returns the entity already in the transaction's cache and ignores the freshly selected row, so a plain `get(id)` followed by `getForUpdate(id)`/`findByIdAndUpdate(id)` in the same transaction takes the lock but still sees the values read **before** it (verified against exposed-dao 1.3.1 sources).
- Lock first: see `docs/agents/transactions-and-locking.md`.
- Return `Unit` from datasource writes unless a caller branches on the result. A `Boolean` that only tests assert on is dead API; assert the guard through the stored state instead.
- Read a row back with `findById(...)?.toDomain()`, not a hand-rolled `selectAll().where { }` + `wrapRow`. Do not re-read after a write either — `new(...)` and `findByIdAndUpdate(...)` return the entity, so `.toDomain()` on it is enough.
- Entity referrers lazy-load one query per parent, so a read returning **many** rows needs `.with(Entity::children)` to batch them. Today's reads are all effectively single-row (`BusinessTable.userId` is a `uniqueIndex`, so a user owns at most one business), so plain referrers are correct — revisit if that changes.
- Rows that belong to an entity should reach it through a real FK so they are a DAO referrer (`val grants by XEntity referrersOn Table.ownerId`, eager-loaded with `.with(...)`), not through a manual join. Exposed's `referrersOn` follows exactly one FK column's `referee` — a logical link over several columns (e.g. grants keyed by `(user_id, business_id)`) cannot be a referrer, so add the owner's id column (`business_permission_grants.employee_id`, backfilled by a join in the migration) instead. Reference: `EmployeeEntity.grants`.
- A request DTO that replaces a whole entity as a PUT body (`EmployeeUpdateModel` for `Employee`) should reuse the entity's `@ProtoNumber`s for the fields it keeps, so older clients still sending the full entity decode cleanly — the extra fields are simply ignored.
- **Race-safe get-or-create** (e.g. resolving a foreign identity's local row by an id it doesn't yet know, provisioning it on first use): back it with a DB-level unique index on the lookup key (composite `uniqueIndex(colA, colB)` in the table's `init { }` block is fine even when one column is nullable — MariaDB treats NULLs as distinct, so unrelated nullable rows don't collide), then in the datasource do find → insert, and on failure re-find before giving up. `onConstraintFailure { }` cannot be reused here since its recovery lambda returns `Nothing` (it is only for turning a race into a thrown domain error) — inside the same `dbQuery { }` block a driver exception hasn't been mapped yet either, so catch it with `runCatching` and classify it yourself via `throwable.toDomain() is Error.UniqueConstraintFailed` (`com.bookk.core.data.map.toDomain`) before re-querying. Reference: `ClientDataSourceImpl.getOrCreateIntegratedClient`.

- **A library-owned, write-once table uses the Exposed DSL, not a DAO entity.** A table a library creates per service schema at runtime (`SubjectKeyTable(schema)` in `library/encryption`) can't back the `companion object : DecoratorUuidEntityClass<X>(XTable)` pattern, which needs one `object` table. Its rows are only inserted, read and deleted, never updated, so the entity rules above buy nothing. Map rows in one private `ResultRow.toX()` in the datasource. Write get-or-create as `insertIgnore` followed by a `forUpdate()` re-read in the same `dbQuery { }`: the re-read returns whichever writer won, and DAO's deferred `new { }` would surface the duplicate key at a later statement instead. Domain aggregates in a service's own schema still use DAO entities.

- **An Exposed FK column accessed through `val x by Table.column` on a DAO entity is typed `EntityID<Uuid>`, not `Uuid`.** Comparing it with `==` against a plain `Uuid` (e.g. `it.businessId == someUuid` after a `findById(id)`) always evaluates to `false` — the types never match structurally — silently making a scoping check useless instead of failing loudly. Unwrap with `.value` first (`it.businessId.value == someUuid`), or push the comparison into the Exposed DSL instead (`Table.businessId eq someUuid` inside `find { }`), which handles the conversion correctly.
