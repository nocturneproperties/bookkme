# Exhausted events

An event whose handler fails every attempt is parked in its consumer's `exhausted_event` table. It is then retried only when someone replays it. A parked `AuthEvent.UserDeleted` is an erasure that hasn't run, so parked events raise an alert and can be replayed without touching the code.

## How an event gets parked

Each consumer group (`<service>_group`) retries a failing event through its own DLT topic (`<topic>_<consumerGroup>_dlt`, `DltRetry`), with backoff, up to `DltRetry.DEFAULT_MAX_ATTEMPTS` (3) attempts. The last failure is saved to the consumer's own schema (`KafkaEventConsumer` / `EmbeddedEventConsumer` → `ExhaustedEventDataSource.save`): the original topic, the encoded event, the attempt count and the DLT chain's own random key (`idempotency_key`, unique). Only the consumer group that failed parks the event. Other services that handled the same event successfully are unaffected.

## Replay

`ExhaustedEventReplayer` (`core/data/eventstreaming/impl`, one per service scope, bound in both transport modules) runs a loop on the application scope:

```mermaid
flowchart TD
    start([startEventHandling: every EventHandler started, then every replayer]) --> once{replay of parked rows<br/>already requested?}
    once -- no --> request["requestReplayOfAll()<br/>UPDATE exhausted_event SET replay_requested = TRUE"]
    request -- fails --> drain
    request -- ok --> drain
    once -- yes --> drain
    drain["READ COMMITTED transaction<br/>lockReplayRequested(100) FOR UPDATE"] --> each{"consumer.replay(event)"}
    each -- true --> delete["delete(ids)"]
    each -- false --> clear["clearReplayRequest(ids)<br/>log: Cannot replay a parked event"]
    each -- throws --> rollback[rollback, rows stay requested]
    delete --> more{batch full?}
    clear --> more
    more -- yes --> drain
    more -- no --> gauge
    rollback --> gauge
    gauge["countByOriginalTopic()<br/>gauge events_exhausted{consumer_group, topic}"] --> wait[wait 1 minute] --> once
```

- **After 30 days** a parked row is deleted (`ExhaustedEventReplayerImpl.DEFAULT_RETENTION`, checked on every tick through `deleteParkedBefore`, logged as a warning), because its payload can hold personal data. Replay or fix it within that time; its `EventsExhausted` alert resolves when it goes.
- **On every start** the replayer marks every row parked so far for replay (retried on the next tick if the database is down), so deploying a fix replays what the bug parked.
- **By hand**: `UPDATE exhausted_event SET replay_requested = TRUE WHERE …` in the consumer's schema. The next tick, within a minute, replays it. Every instance runs the loop, and the row lock makes each row replay once.
- **Personal data**: a parked `PersonalEvent`'s whole payload is sealed with the consumer's own subject key (`personal_data_encrypted = true`) and opened when the row is locked for replay. Both happen in `library/outbox` (`SealingExhaustedEventDataSource` around the plain rows, with the scope's `PersonalDataCipher`), so the consumers in core only see the outcome (`Parked`, `ParkedPayload`); a parked event whose data subject was erased since is dropped on replay (`replay` returns `true`, so the row is deleted), one whose payload can't be opened is refused like an undecodable one, and one whose subject was already erased when it failed is not parked at all. Every other row, including those parked before 0.18.0, replays as stored.
- **`consumer.replay(event)`** publishes the event to *this* consumer group's DLT topic with attempt 0 and a new random key, so it gets every attempt again and no other service sees it. Kafka re-sends the stored payload (`DltEvent`). The embedded transport decodes it with the receiver's type and publishes an `EmbeddedDltEvent`. It returns `false`, and the row is kept with its request cleared, when this consumer has no receiver for the topic any more, when the payload no longer decodes, or (embedded) when the consumer hasn't started yet. `startEventHandling` starts the replayers after every `EventHandler`, so the embedded subscriptions exist before the first replay.
- **An event that fails again** is parked again as a new row under its new key. The replayed row was already deleted when the replay committed, and the new row isn't requested, so a poison event costs three attempts per start, not a loop.
- Delivery stays at-least-once. If the commit fails after a publish, the row is replayed again later, and consumers deduplicate on the event's own idempotency key (`IdempotentDelivery`). An embedded replay lives in memory, so a crash between the commit and the handler loses it, like any other embedded event.

## Alert

`EventsExhausted` (`deployment/prod/monitoring/prometheus/node_alerts.yml`, critical) fires while `max by (consumer_group, topic) (events_exhausted) > 0`. `max` and not `sum`, because every instance reports the same table (two during a deploy). A topic disappears from the gauge once its rows are gone, which resolves the alert.

## Operator steps

1. Find the failure in the logs: `Error while processing event for topic: <topic>` in the consumer's service.
2. Fix the cause. A bug fix replays by itself on deploy. For a cause outside the code (a provider outage, bad data fixed by hand), request the replay:
   ```sql
   UPDATE <schema>.exhausted_event SET replay_requested = TRUE WHERE original_topic = '<topic>';
   ```
   The schema is the consumer's service: `auth_group` → `authorization`, every other `<service>_group` → `<service>`.
3. The alert clears within a minute or two once the replay succeeds. If the event fails again, it is parked again and the alert stays.
4. A row logged as `Cannot replay a parked event` can't be replayed by this version. Handle it by hand, then `DELETE` it, or the alert stays.

## Device events after account scoping

`AuthEvent.DeviceCreated` keeps its wire format. With notifications V4, a repeated `(authId, deviceUuid, userId)` succeeds while preserving the existing token and language; different accounts can share an installation UUID. A conflicting user for an existing account/device pair still fails. Before replaying a parked creation, check authorization still has that account/device association: creation and deletion events on different topics have no relative ordering, so an obsolete creation can recreate a deleted association.

`AuthEvent.DeviceDeleted` and `AuthEvent.DeviceLanguageUpdated` now carry `authId` as an added ProtoBuf field. Legacy payloads decode with `Uuid.NIL` and the notification handler rejects them, keeping them parked instead of changing another account's device. Determine the original account from source records or logs, apply the intended change to that account/device pair, and delete only that reconciled exhausted-event row. A missing account cannot safely be inferred from the UUID alone. New events include the account on every producer path, including inactive-device cleanup. Failed sign-up only compensates the remote user: its device insert and creation outbox entry roll back together, so no device deletion is emitted.
