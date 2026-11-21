# Remove saved business

`DELETE /api/appointments/businesses/{businessId}` → `RemoveSavedBusiness`

A client removes a business from their saved businesses (see [Save
business](save-business.md)), which frees a place under the limit of 100.
Removing a business that isn't saved changes nothing. The delete matches the
caller's own user id and the business id, so nobody can reach another
user's list. No event is sent.

```mermaid
flowchart TD
    Start([DELETE /api/appointments/businesses/businessId]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> Delete["SavedBusinessDataSource.remove userId businessId - DELETE saved_business WHERE user_id AND business_id"]
    Delete --> R204([204 No Content])
```
