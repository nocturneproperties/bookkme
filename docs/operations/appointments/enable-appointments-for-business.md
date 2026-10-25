# Enable appointments for business

`POST /api/appointments/enabled/{businessId}` → `EnableAppointmentsForBusiness`

Turns the appointments plugin on for a business: pulls a snapshot of the
business (name, address, time zone, schedule) from the business service,
subscribes it locally, seeds the caller with full control
(`ResourcePermission.FULL`) in the appointments service's own permission
table, and creates default settings. All three writes share one
transaction; a repeat call collides on the subscription's unique
constraint and surfaces as `PLUGIN_ALREADY_ENABLED`.

```mermaid
flowchart TD
    Start([POST /api/appointments/enabled/businessId]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> GetPerm[BusinessClient.getPermission userId businessId BUSINESS]
    GetPerm -- request failed --> RErr([Propagate business-service error])
    GetPerm -- caller suspended in the business --> R403s([403 BUSINESS_EMPLOYEE_ACCESS_SUSPENDED 200034 - propagated from the business service])
    GetPerm -- ok --> GetBiz[BusinessClient.getBusinessById businessId]
    GetBiz -- request failed --> RErr
    GetBiz -- ok --> Tx[[Begin transaction]]
    Tx --> AssertOwner{permission covers FULL?}
    AssertOwner -- No --> R404([404 Error.OperationNotAllowed])
    AssertOwner -- Yes --> Attach[AppointmentSubscriptionDataSource.attachBusiness BusinessSnapshot]
    Attach --> SetPerm[AppointmentPermissionDataSource.setPermission userId businessId FULL]
    SetPerm --> CreateSettings[AppointmentSettingsDataSource.create default AppointmentSettings]
    CreateSettings --> Constraint{Unique constraint violated?}
    Constraint -- Yes --> R422([422 PLUGIN_ALREADY_ENABLED 300009])
    Constraint -- No --> R204([204 No Content])
```
