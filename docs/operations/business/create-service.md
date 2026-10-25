# Create service

`POST /api/business/{businessId}/service` → `CreateService`

The price is always stored in the business currency: the amount from the
body is kept and its currency is replaced by `business.currency`, so every
service of a business shares one currency and quote/appointment totals can be
summed. An amount the business currency cannot represent (`100.50` for a `JPY`
business) is rejected rather than rounded; trailing zeros (`100.00` → `JPY
100`) are fine. The business row is locked with `RowLock.SHARED` (`LOCK IN
SHARE MODE`): service writes do not block each other, but a currency change in
[`UpdateBusiness`](update-business.md) (`RowLock.EXCLUSIVE`) waits for them,
and they wait for it. A stored currency that is not a supported currency
(legacy data such as `usd`) is rejected until the business currency is fixed.

The group is resolved server-side with `getServiceGroup(group.id,
businessId)`: a group of another business is rejected, and the stored group
replaces whatever group object the client sent, in both the row and the
response. [`DeleteServiceGroup`](delete-service-group.md) takes the business
lock exclusively, so the group cannot disappear between the check and the
write.

Lock first: the business row lock is the transaction's first statement,
before the permission lookup. InnoDB fixes a REPEATABLE READ snapshot at the
first plain read, so taking the lock first makes every later plain read see
whatever the previous lock holder committed; locking after a plain read would
leave the checks on a stale snapshot (`BusinessRowLockMariaDbTest`).

```mermaid
flowchart TD
    Start([POST /api/business/businessId/service]) --> PathCheck{path businessId == body.businessId?}
    PathCheck -- No --> R400([400 Bad Request])
    PathCheck -- Yes --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> NameCheck{service.name blank?}
    NameCheck -- Yes --> R422a([422 BUSINESS_SERVICE_NAME_VALIDATION_ERROR 200008])
    NameCheck -- No --> Tx[[Begin transaction]]
    Tx --> Lock[BusinessDataSource.lockBusiness businessId SHARED - LOCK IN SHARE MODE, first statement of the transaction]
    Lock --> Found{business found?}
    Found -- No --> R404nf([404 Error.NotFound])
    Found -- Yes --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller SERVICES.update?}
    Perm -- No --> R404([404 Error.OperationNotAllowed])
    Perm -- Yes --> Supported{CurrencyValidator.isValid business currency?}
    Supported -- No --> R422d([422 BUSINESS_SERVICE_BUSINESS_CURRENCY_INVALID 200038])
    Supported -- Yes --> Price{price amount fits the business currency's decimal places?}
    Price -- No --> R422c([422 BUSINESS_SERVICE_PRICE_PRECISION_INVALID 200037])
    Price -- Yes --> Group[ServiceDataSource.getServiceGroup group.id businessId - scoped to the business]
    Group --> GroupFound{group belongs to the business?}
    GroupFound -- No --> R422e([422 BUSINESS_SERVICE_GROUP_NOT_EXISTS 200012])
    GroupFound -- Yes --> Create[ServiceDataSource.createService service with the stored group and the price re-denominated in the business currency]
    Create --> Constraint{Unique constraint violated - name exists?}
    Constraint -- Yes --> R422b([422 BUSINESS_SERVICE_EXISTS 200007])
    Constraint -- No --> R200([200 Created Service])
```
