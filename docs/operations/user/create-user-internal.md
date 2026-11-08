# Create user (internal)

`POST /api/internal/user` → `CreateUser`

Service-to-service route (no `authenticate {}`/`Security: jwt` — reachable
only from inside the deployment, e.g. from the authorization service
during [Sign-up](../authorization/sign-up.md)). The body is a `NewUser`:
the profile fields plus the legal document versions the user accepted in
the app (`acceptedDocuments`, required). A body without it, such as the
`User` an older auth service sends, is rejected with 400 before the
operation runs. Every sent version must be published and in effect, and
every document with a version in effect must be accepted in exactly that
version (see [Legal documents](../../legal-documents.md)); a document with
no published version needs nothing. Both checks run before the profile is
inserted, and the profile, its acceptances and a `UserEvent.Updated`
carrying the new profile are written in one transaction, so other
services learn the verified sign-up email without waiting for a profile
edit.

```mermaid
flowchart TD
    Start([POST /api/internal/user]) --> Decode{Body has acceptedDocuments?}
    Decode -- No --> R400([400])
    Decode -- Yes --> Tx[[Begin transaction]]
    Tx --> Each[For each sent document - LegalDocumentDataSource.getVersion document version]
    Each -- not found or effective_at in the future --> R422a([422 LEGAL_DOCUMENT_VERSION_UNKNOWN 100003])
    Each -- all in effect --> InEffect[LegalDocumentDataSource.getVersionsInEffect now]
    InEffect -- a version in effect not sent --> R422b([422 LEGAL_DOCUMENTS_NOT_ACCEPTED 100004])
    InEffect -- all accepted --> Insert[UserDataSource.insertNewUser - id generated]
    Insert --> Record[LegalDocumentDataSource.recordAcceptance userId document version now - INSERT IGNORE, per sent document]
    Record --> Event[eventProducer.send UserEvent.Updated userId name lastName email phone now]
    Event --> Commit[[Commit transaction]]
    Commit --> R200([200 UserId])
```

**Consumed by:** `UserEvent.Updated` → [business: sync denormalized client/
employee copies](../business/on-user-updated.md) and [notifications: sync
the email delivery target](../notifications/on-user-updated.md).
