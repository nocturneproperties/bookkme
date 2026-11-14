# React to user profile update

Kafka topic `UserEvent.Updated` → `NotificationEventHandler` → `UpdateTargetInformation`

Produced by [Create user (internal)](../user/create-user-internal.md) at
sign-up, [Update user](../user/update-user.md) and [Store a verified
email](../user/change-user-email-internal.md). Keeps the user's email
delivery target in sync with their profile email, creating default
notification settings first when the user has none. An update that arrives
after the account was deleted (`SubjectKeys.isShredded`) creates neither
settings nor a target and is still marked processed. The address is stored
encrypted under the user's key (`TargetCipher`). Every profile email
is verified before it is stored, so this table only ever holds verified
addresses, and `EmailNotificationSender` mails nothing else: it reads the
address and the language of the device the user signed in to last
(`DeviceDataSource.getLastUsedLanguage`, English without one) in a
transaction, then sends through `EmailSender` outside it. A user with no
address gets no email, and a failed send is logged without failing the
event or the other channels.

```mermaid
flowchart TD
    Start([Consume UserEvent.Updated]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Erased{SubjectKeys.isShredded userId - account already deleted?}
    Erased -- Yes --> Ack
    Erased -- No --> GetSettings[GetNotificationSettings userId]
    GetSettings -- none --> Create[NotificationSettingsDataSource.upsert default settings - EMAIL offered, off]
    Create --> UpdateEmail
    GetSettings -- found --> UpdateEmail{NotificationTargetDataSource.updateEmail userId email updatedAt - encrypted, row matched?}
    UpdateEmail -- Yes --> Ack([Marked processed])
    UpdateEmail -- No, stale or missing --> HasEmail{NotificationTargetDataSource.hasEmail userId - row exists?}
    HasEmail -- No --> Insert[NotificationTargetDataSource.insertEmail userId email updatedAt - encrypted under the user's key]
    HasEmail -- Yes, stale or inserted concurrently --> Ack
    Insert --> Ack
```
