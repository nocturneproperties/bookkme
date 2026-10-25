# Delete service group

`DELETE /api/business/{businessId}/service_group/{id}` → `DeleteServiceGroup`

Deletes the group and, per the route's own description, every service that
belongs to it (cascading delete happens at the datasource/table level).

The delete is scoped to the business the permission check ran against
(`WHERE id = ? AND business_id = ?`). A group id of another business matches
nothing and the route still answers 204, exactly like an unknown id, so the
response does not reveal whether that group exists. Before this scoping, a
caller with `SERVICES.delete` in one business could delete any business's
group and, through the cascade, all of its services.

The operation first takes `lockBusiness(businessId, RowLock.EXCLUSIVE)`, so a
deletion waits for in-flight [`CreateService`](create-service.md)/
[`UpdateService`](update-service.md) calls (which hold `RowLock.SHARED` while
they check and reference the group) and blocks new ones until it commits.

```mermaid
flowchart TD
    Start([DELETE /api/business/businessId/service_group/id]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Lock[BusinessDataSource.lockBusiness businessId EXCLUSIVE - FOR UPDATE, first statement of the transaction]
    Lock --> Found{business found?}
    Found -- No --> R404nf([404 Error.NotFound])
    Found -- Yes --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller SERVICES.delete?}
    Perm -- No --> R404([404 Error.OperationNotAllowed])
    Perm -- Yes --> Delete[ServiceDataSource.deleteServiceGroup id businessId - only within the business, cascades to member services]
    Delete --> R204([204 Service group deleted])
```
