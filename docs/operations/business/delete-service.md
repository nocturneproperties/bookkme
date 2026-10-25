# Delete service

`DELETE /api/business/{businessId}/service/{id}` → `DeleteService`

The delete is scoped to the business the permission check ran against
(`WHERE id = ? AND business_id = ?`). A service id of another business matches
nothing and the route still answers 204, exactly like an unknown id, so the
response does not reveal whether that service exists. Before this scoping, a
caller with `SERVICES.delete` in one business could delete any business's
service by id.

No business row lock is taken: a delete running alongside a currency change
in [`UpdateBusiness`](update-business.md) can only make its `hasServices`
check see one service too many, which keeps the currency locked. It can never
let prices in two currencies coexist.

```mermaid
flowchart TD
    Start([DELETE /api/business/businessId/service/id]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller SERVICES.delete?}
    Perm -- No --> R404([404 Error.OperationNotAllowed])
    Perm -- Yes --> Delete[ServiceDataSource.deleteService id businessId - only within the business]
    Delete --> R204([204 Service offering deleted])
```
