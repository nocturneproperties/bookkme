# Transactional outbox

An operation's database write and the event it announces must succeed or
fail together. Kafka can't join a database transaction, so an operation never
talks to Kafka directly: `eventProducer.send` stores the encoded event in the
service's own `outbox_event` table **inside the operation's transaction**,
and a background `OutboxRelay` publishes committed rows afterwards.

- A rolled-back transaction (domain error, crash, or one of the
  `ExposedTransactionManager` conflict retries) rolls back its outbox row too,
  so no event exists for a write that never happened.
- A committed row stays in the outbox until the relay has handed it to the
  transport, so an unreachable broker delays the event instead of losing it.
- Delivery is **at-least-once**: a relay that publishes and then fails before
  committing its delete publishes the batch again on the next pass.

Code: `core/data/eventstreaming/data` (`OutboxEventProducer`, `OutboxRelay`,
`OutboxEventDataSourceImpl`, `OutboxEventTable`, `outboxModule`) and
`core/data/eventstreaming/impl` (transports). Schema: `OUTBOX_EVENT` in
[business](database/business.md),
[authorization](database/authorization.md) and [user](database/user.md).

**Rollout status:** business, authorization and user send through the
outbox. Appointments still sends to the transport directly
(`eventStreamingModule` / `embeddedEventStreamingModule`); notifications
sends no events and still uses those modules for its consumer.

## Overview

The write side is identical in both deployments. Only the transport the
relay hands rows to differs.

```mermaid
flowchart TB
    subgraph TX["transactionManager.transaction { } of the operation"]
        direction TB
        Write["business write<br/>e.g. businessPermissionDataSource.setPermissions"]
        Send["eventProducer.send(BusinessEvent.X)<br/>= OutboxEventProducer"]
        Check{"transactionManager<br/>.isInTransaction()"}
        Encode["ProtoBuf encode<br/>topic, partitionKey, idempotencyKey kept in payload"]
        Enqueue["OutboxEventDataSource.enqueue<br/>INSERT INTO outbox_event"]
        Write --> Send --> Check
        Check -- yes --> Encode --> Enqueue
    end
    Check -- no --> Fail["IllegalStateException<br/>send outside a transaction"]
    Enqueue --> Outcome{"commit?"}
    Outcome -- "rollback / conflict retry" --> Gone["row discarded with the write<br/>no event"]
    Outcome -- commit --> Table[("outbox_event<br/>id BIGINT AUTO_INCREMENT")]

    subgraph RELAY["OutboxRelay.relayBatch() - its own READ COMMITTED transaction"]
        direction TB
        Lock["lockPending(100)<br/>SELECT ... ORDER BY id LIMIT 100 FOR UPDATE<br/>record locks only, no gap lock, so new inserts never wait"]
        Publish["RawProducer.publishAll(records)<br/>Kafka sends every row in id order, then awaits all acks"]
        Acked{"every record<br/>acknowledged?"}
        Delete["delete(ids)<br/>then COMMIT"]
        Rollback["whole batch rolls back<br/>rows stay for the next pass"]
        Lock --> Publish --> Acked
        Acked -- yes --> Delete
        Acked -- no --> Rollback
    end
    Table --> Lock

    subgraph TRANSPORT["RawProducer - chosen by the DI wiring"]
        direction LR
        Kafka["standalone microservice<br/>KafkaEventProducer to Kafka topic"]
        Embedded["monolith<br/>EmbeddedEventProducer: EmbeddedEncodedEvent<br/>into the shared TopicQueueHolder"]
    end
    Publish -.-> Kafka
    Publish -.-> Embedded
```

### Relay loop

`OutboxRelay` is registered as an `EventHandler`, so `startEventHandling(scope)`
starts one relay per service scope, both in a standalone microservice and in
the monolith.

```mermaid
flowchart LR
    Start(["startEventHandling(scope)"]) --> Batch["relayBatch()"]
    Batch --> Full{"relayed == batchSize?"}
    Full -- "yes - backlog" --> Batch
    Full -- "no - partial or empty" --> Idle["delay(idleDelay = 500 ms)"]
    Batch -- failure --> Log["log error"] --> Idle
    Idle --> Batch
```

## Standalone microservice (Kafka)

Wiring: `eventStreamingTransportModule(Scope, SERVICE_NAME)` +
`outboxModule(Scope)`.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Op as Operation (business)
    participant DB as business schema
    participant Relay as OutboxRelay (business)
    participant Kafka
    participant Consumer as KafkaEventConsumer (appointments)
    participant Store as EventIdempotencyStorage
    participant Handler as EventHandler receiver

    Client->>Op: PUT .../permissions
    Op->>DB: BEGIN, write rows, INSERT outbox_event
    Op->>DB: COMMIT
    Op-->>Client: 200
    loop every pass
        Relay->>DB: BEGIN (READ COMMITTED), SELECT outbox rows FOR UPDATE
        Relay->>Kafka: publishAll: send every row, then await all acks
        Relay->>DB: DELETE relayed rows, COMMIT
    end
    Kafka->>Consumer: record (same partition for same partitionKey)
    Consumer->>Consumer: decode payload with the receiver's registered KType
    Consumer->>Store: claim(consumerGroup, topic, idempotencyKey, deliveryId), atomic SET NX
    opt IN_PROGRESS (claim held by an unfinished or crashed delivery)
        Consumer->>Store: claim again every second, for up to the claim TTL (1 min)
    end
    alt ALREADY_PROCESSED (relay re-sent a batch)
        Consumer->>Consumer: skip
    else still IN_PROGRESS after the claim TTL
        Consumer->>Kafka: DltEvent to topic_consumerGroup_dlt, retried like a handler failure
    else CLAIMED
        Consumer->>Handler: onEvent(event), runs its own transaction
        alt handler succeeds
            Consumer->>Store: markProcessed (kept 1h)
        else handler fails (a cancellation it threw itself counts as a failure)
            Consumer->>Store: release(deliveryId): deleted only while the claim is still this delivery's, so the retry can claim it again
            Consumer->>Kafka: DltEvent to topic_consumerGroup_dlt via KafkaEventProducer directly (not the outbox)
            Note over Consumer,Kafka: retried with backoff, after DEFAULT_MAX_ATTEMPTS saved to exhausted_event
        end
    end
```

## Monolith (embedded)

Wiring: `embeddedEventStreamingTransportModule(Scope, SERVICE_NAME)` + `outboxModule(Scope)`
per service, plus one root `topicQueueHolderModule()`. Every service keeps its
own schema, its own `outbox_event` table and its own relay. Only the in-memory
`TopicQueueHolder` is shared.

```mermaid
sequenceDiagram
    autonumber
    participant Op as Operation (business)
    participant DB as business schema
    participant Relay as OutboxRelay (business scope)
    participant Queue as TopicQueueHolder (shared, in memory)
    participant A as EmbeddedEventConsumer (appointments)
    participant N as EmbeddedEventConsumer (notifications)

    Op->>DB: BEGIN, write rows, INSERT outbox_event, COMMIT
    loop every pass
        Relay->>DB: BEGIN (READ COMMITTED), SELECT outbox rows FOR UPDATE
        Relay->>Queue: publishAll = one EmbeddedEncodedEvent(topic, partitionKey, payload) per row
        Relay->>DB: DELETE relayed rows, COMMIT
    end
    par fan-out to every subscriber of the topic
        Queue->>A: EmbeddedEncodedEvent
        A->>A: decode with own KType, run receiver
    and
        Queue->>N: EmbeddedEncodedEvent
        N->>N: decode with own KType, run receiver
    end
    Note over A,N: undecodable payload - logged and dropped
    Note over A,N: failing receiver - in-memory EmbeddedDltEvent retries, then exhausted_event
```

## What happens when something fails

| Failure | Outcome |
|---|---|
| Operation throws or its transaction rolls back | The outbox row rolls back with the write. No event. |
| `ExposedTransactionManager` retries a conflicting block | Only the attempt that commits leaves a row, so retries don't multiply events. |
| `send` called outside a transaction | `IllegalStateException`. The event is never stored. |
| Broker unreachable, or `publish` throws | The relay's batch rolls back and the rows stay. Retried after `idleDelay`. Business writes keep inserting outbox rows meanwhile: the relay runs at READ COMMITTED, so its `FOR UPDATE` takes no gap lock (`OutboxRelayMariaDbTest`). |
| Crash after publish, before the delete commits | The batch is published again. Kafka and embedded consumers skip it through `EventIdempotencyStorage` (same `idempotencyKey`, keyed per consumer group, so every consuming service still handles it once). |
| Consumer crashes mid-handler | Its claim stays for up to 1 minute. A redelivery in that window waits for it (re-claiming every second), then handles the event once the claim expires. Only a claim still held after the TTL sends the event through the DLT retries. A handler is expected to finish within the claim TTL. |
| Handler outlives its claim, then fails | Another delivery may already have claimed the event (or finished it). The late `release` is a compare-and-delete on its own `deliveryId` (`CacheClient.deleteIfEquals`, a Lua script on Redis), so it frees neither the other delivery's claim nor the processed marker, and a later duplicate is still skipped. The event itself can be handled twice in this case: the slow handler and the one that took over both ran. |
| Idempotency store (Redis) unreachable | A failed claim is retried through the DLT like a handler failure. A failed `markProcessed` is logged only: the handler already succeeded and is not run again. |
| Two relays for the same schema | The second relay's `FOR UPDATE` waits for the first to commit, then finds the rows deleted. Each row is published once, in id order (`OutboxRelayMariaDbTest`). |
| Consumer can't decode a payload | Logged and dropped, as with Kafka. Later events keep flowing. |
| Receiver keeps failing | DLT retries with backoff, then `exhausted_event`. Consumer-side DLT sends go to the transport directly, never through the outbox. Each consumer group has its own DLT topic (`<topic>_<consumerGroup>_dlt`), so a service retries only its own failures. |
| Monolith process dies | Rows still in `outbox_event` survive and are relayed on restart. Events already handed to the in-memory queue but not yet consumed are lost. |

## Ordering

Rows leave the relay in `id` order. Two writes to the same aggregate are
serialized by its row lock, so the later one inserts (and commits) its outbox
row after the earlier one. A higher id from another aggregate can commit
before a lower one. The relay never tracks a "last sent id"; each pass takes
every pending row, so a late-committing lower id is still picked up. Kafka
then keeps per-`partitionKey` order within a partition.
