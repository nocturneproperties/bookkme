# Create service group

`POST /api/business/{businessId}/service_group` → `CreateServiceGroup`

```mermaid
flowchart TD
    Start([POST /api/business/businessId/service_group]) --> PathCheck{path businessId == body.businessId?}
    PathCheck -- No --> R400([400 Bad Request])
    PathCheck -- Yes --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> NameCheck{group.name blank?}
    NameCheck -- Yes --> R422a([422 BUSINESS_SERVICE_GROUP_VALIDATION_ERROR 200011])
    NameCheck -- No --> Tx[[Begin transaction]]
    Tx --> Suspended{BusinessPermissionDataSource.getPermission - caller is a suspended employee of the business?}
    Suspended -- Yes --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034])
    Suspended -- No --> Perm{caller SERVICES.update?}
    Perm -- No --> R404([404 Error.OperationNotAllowed])
    Perm -- Yes --> Create[ServiceDataSource.createServiceGroup service]
    Create --> Constraint{Unique constraint violated - name exists?}
    Constraint -- Yes --> R422b([422 BUSINESS_SERVICE_GROUP_EXISTS 200010])
    Constraint -- No --> R200([200 Created ServiceGroup])
```
