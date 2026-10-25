# React to an employee joining the business

Kafka topic `BusinessEvent.EmployeeInvitationRedeemed` → `NotificationEventHandler` → `SendNotification`

Produced by [Join business](../business/join-business.md).
Notifies the inviter (not the newly-joined employee) that their invitation
was redeemed. Always allowed, channel selection only.

```mermaid
flowchart TD
    Start([Consume BusinessEvent.EmployeeInvitationRedeemed]) --> Dedup{idempotencyKey already processed?}
    Dedup -- Yes --> Skip([Skip - already handled])
    Dedup -- No --> Send[SendNotification to=inviterUserId]
    Send --> GetSettings[NotificationSettingsDataSource.getByUserId inviterUserId]
    GetSettings -- not found --> Dlt([Sent to BusinessEvent.EmployeeInvitationRedeemed_dlt topic, logged - no auto-retry])
    GetSettings -- found --> Channels[For each enabled channel: senderMap.channel.send inviterUserId notification - always allowed]
    Channels --> Ack([Marked processed])
```
