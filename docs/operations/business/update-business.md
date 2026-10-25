# Update business

`PUT /api/business/{id}` → `UpdateBusiness`

Partial update: every field on `BusinessUpdateModel` is nullable and only
non-null fields are applied. String fields are silently truncated to the
entity's max length rather than rejected, except `currencyCode`, which is
validated. Publishes `BusinessEvent.Updated`
so other services (appointments' business snapshot, in particular) stay in
sync.

The currency is locked once the business has a service: every service price
and every appointment snapshot taken from it is denominated in it, and money
in two currencies cannot be summed. Re-sending the stored currency is a no-op
and always allowed, so clients that send the full model keep working.
When `currencyCode` is sent, `lockBusiness(id, RowLock.EXCLUSIVE)`
(`SELECT … FOR UPDATE`) runs first, waiting for every
`CreateService`/`UpdateService` holding `RowLock.SHARED` on the same row.
Lock first: the business row lock is the transaction's first statement,
before the permission lookup. InnoDB fixes a REPEATABLE READ snapshot at the
first plain read, so taking the lock first makes every later plain read see
whatever the previous lock holder committed; locking after a plain read would
leave the checks on a stale snapshot (`BusinessRowLockMariaDbTest`).
The stored-currency checks run after the permission check, so a caller
without access learns nothing beyond the 404 it would get anyway.

A stored currency that `CurrencyValidator` rejects (written before currency
validation existed, e.g. `usd`) does not lock: its prices cannot be in that
currency, and without this the business could neither price services nor
repair its currency.

```mermaid
flowchart TD
    Start([PUT /api/business/id]) --> PathCheck{path id == body.id?}
    PathCheck -- No --> R400a([400 Bad Request])
    PathCheck -- Yes --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> CurrencyGiven{body.currencyCode present?}
    CurrencyGiven -- Yes --> Lock[BusinessDataSource.lockBusiness id EXCLUSIVE - FOR UPDATE, first statement of the transaction]
    Lock --> Found{business found?}
    Found -- No --> R404nf([404 Error.NotFound])
    Found -- Yes --> Suspended
    CurrencyGiven -- No --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller BUSINESS.update?}
    Perm -- No --> R404([404 Error.OperationNotAllowed])
    Perm -- Yes --> ScheduleGiven{body.schedule present?}
    ScheduleGiven -- Yes --> WorkHours{any active day with empty workingTime?}
    WorkHours -- Yes --> R422a([422 BUSINESS_ACTIVE_DAY_WITHOUT_WORK_HOURS 200019])
    WorkHours -- No --> DayOffRange{any dayOff.start > end?}
    DayOffRange -- Yes --> R422b([422 BUSINESS_INVALID_DAY_OFF_RANGE 200020])
    DayOffRange -- No --> CurrencySent{body.currencyCode present?}
    ScheduleGiven -- No --> CurrencySent
    CurrencySent -- No --> Truncate
    CurrencySent -- Yes --> SameCurrency{currencyCode == stored currency?}
    SameCurrency -- Yes --> Truncate
    SameCurrency -- No --> ValidCurrency{CurrencyValidator.isValid currencyCode?}
    ValidCurrency -- No --> R422c([422 BUSINESS_INVALID_CURRENCY 200035])
    ValidCurrency -- Yes --> StoredValid{CurrencyValidator.isValid stored currency?}
    StoredValid -- No --> Truncate
    StoredValid -- Yes --> HasServices{ServiceDataSource.hasServices id?}
    HasServices -- Yes --> R422d([422 BUSINESS_CURRENCY_LOCKED 200036])
    HasServices -- No --> Truncate
    Truncate[Truncate name/description/address/socials to max length]
    Truncate --> Update[BusinessDataSource.updateBusiness updatedModel updatedAt]
    Update --> Event[eventProducer.send BusinessEvent.Updated]
    Event --> R204([204 No Content])
```

**Consumed by:** `BusinessEvent.Updated` → [appointments: refresh the
cached business snapshot](../appointments/on-business-updated.md).
