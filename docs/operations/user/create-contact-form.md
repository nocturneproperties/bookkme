# Send contact form

`POST /api/user/contact_us` → `CreateContactForm`

Free-text support submission tied to the caller. Oversized `text`/
`usageLogs` are silently truncated to their upper bounds rather than
rejected, when `form.isBoundCapRequired` applies.

```mermaid
flowchart TD
    Start([POST /api/user/contact_us]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Tx[[Begin transaction]]
    Tx --> CapCheck{form.isBoundCapRequired?}
    CapCheck -- Yes --> Truncate[Truncate text to TEXT_UPPER_BOUND, usageLogs to LOGS_UPPER_BOUND]
    CapCheck -- No --> Save
    Truncate --> Save[CommunicationDataSource.saveContactForm form - status NEW]
    Save --> R201([201 Contact form submitted])
```
