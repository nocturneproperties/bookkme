# Save business

`POST /api/appointments/businesses/{businessId}` → `SaveBusiness`

A client adds a business to their saved businesses, typically when they open
the business's link in the Telegram Mini App, so its Home screen can offer it
for booking again (`GET /api/appointments/businesses`). Only a business that
takes appointments right now can be saved; saving one already saved changes
nothing, whatever its state. A user can save at most 100 businesses
(`SaveBusiness.MAX_SAVED_BUSINESSES`), a soft cap: saves by one user aren't
serialized, so two at the same moment can end one over it (the developer's
decision, to avoid a per-user lock row); removing one (see [Remove saved
business](remove-saved-business.md)) frees a place.

The account tombstone is read first with a shared lock, like the booking
paths, so a save racing account deletion either lands before it and is
deleted by it, or sees the deletion and answers 404. Then the business's
`business_has_appointments` row is read with a shared lock, so the business
can't be erased between the check and the insert (which would fail on the
foreign key with a 500). Every plain read comes after these locks; a business
already saved is a no-op even if it stopped taking appointments, and the
insert is an `INSERT IGNORE`, so two concurrent saves of the same business
keep one row with the first time. No event is
sent.

```mermaid
flowchart TD
    Start([POST /api/appointments/businesses/businessId]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Deleted["DeletedUserDataSource.isDeletedForShare userId - LOCK IN SHARE MODE, first statement"]
    Deleted -- deleted --> R404([404 Error.OperationNotAllowed])
    Deleted -- not deleted --> Business["AppointmentSubscriptionDataSource.isBusinessEnabledForShare businessId - LOCK IN SHARE MODE on business_has_appointments"]
    Business --> Saved{SavedBusinessDataSource.isSaved userId businessId}
    Saved -- Yes --> R204a([204 No Content - nothing changed])
    Saved -- No --> Enabled{business enabled?}
    Enabled -- "No, or the business never enabled appointments" --> R422a([422 APPOINTMENTS_NOT_ENABLED 300030])
    Enabled -- Yes --> Count{"SavedBusinessDataSource.count userId >= 100?"}
    Count -- Yes --> R422b([422 SAVED_BUSINESSES_LIMIT_REACHED 300031])
    Count -- No --> Insert["SavedBusinessDataSource.save userId businessId now - INSERT IGNORE saved_business"]
    Insert --> R204([204 No Content])
```
