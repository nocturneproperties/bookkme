# bookk-server

Backend for **Bookk** — an appointment-booking platform for small businesses. Clients discover a business, request a time slot, and get notified; owners manage their services, staff, working hours, and bookings.

Written in Kotlin as a **modular monolith**: one Gradle build, one shared core, five independently deployable Ktor services that talk over Kafka.

---

## Contents

- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Running the local stack](#running-the-local-stack)
- [Configuration](#configuration)
- [Testing](#testing)
- [Database migrations](#database-migrations)
- [Deployment](#deployment)
- [Contributing](#contributing)

---

## Architecture

### Services

| Service | Port (local) | Responsibility |
|---|---|---|
| `authorization` | 8000 | Account registration, WebAuthn/passkey login, JWT issuing & refresh, device sessions |
| `user` | 8001 | User profiles, contact forms, account deletion |
| `business` | 8002 | Businesses, services & service groups, employees and invitations, clients, quotes |
| `appointments` | 8003 | Booking requests, approval flow, schedules, day-offs, appointment history |
| `notifications` | 8004 | Push notification targets, device tokens, per-user notification settings |

Each service owns its own database schema. Nothing reaches across a schema boundary — cross-service reads go through a `client/` module, and cross-service writes are driven by Kafka events.

### Module layering

Every service is split into the same five modules, and the dependency direction is enforced by the build:

```
service/<svc>/
├── domain/api/      operation interfaces, entities, error codes   ← no dependencies
├── domain/impl/     operation implementations + Koin DI + tests
├── data/source/     datasource interfaces
├── data/            Exposed tables/entities, datasource impls, migrations
├── microservice/    Ktor routes (typed Resources), main(), route tests
└── client/          events and API surface consumed by other services
```

A business rule lives in exactly one place: an **operation** — a single-method interface in `domain/api`, implemented in `domain/impl`, returning `Result<T>`. Routes are thin; they authenticate, deserialize, call one operation, and map the result to HTTP.

Shared infrastructure lives outside the services:

```
core/       Result/Error types, Ktor server bootstrap, auth, Exposed helpers, Kafka event streaming, Redis cache, i18n
library/    money, idempotency, permissions, schedule, scheduler, request signing
build-src/  Gradle convention plugins (bookk.microservice, bookk.data, bookk.domain.api, …)
```

### Conventions worth knowing up front

- **Wire format is ProtoBuf**, not JSON — all request and response bodies use `application/x-protobuf`.
- **OpenAPI specs are generated from source** by the Ktor compiler plugin, parsed out of route KDoc.
- **Errors are typed.** Each operation declares a nested `sealed interface Error`; every case carries an HTTP status and a stable numeric code from `<Svc>ErrorCodes`. Codes are allocated in blocks of 100 000 per service.
- **Permission failures return 404, not 403** — intentional, so probing cannot confirm that a resource exists.
- **Background work is in-process.** Services register recurring jobs via the `Scheduler` plugin (e.g. appointments marks bookings completed every 5 minutes and prunes outdated requests hourly).

---

## Tech stack

| | |
|---|---|
| Language | Kotlin, JVM toolchain 21 |
| HTTP | Ktor 3.5 (CIO engine), typed `Resources` routing |
| DI | Koin 4 |
| Persistence | Exposed 1.0 (DAO + DSL) on MySQL/MariaDB, Flyway migrations |
| Messaging | Apache Kafka (KRaft) |
| Cache | Redis via Lettuce |
| Serialization | kotlinx.serialization (ProtoBuf) |
| Auth | Yubico WebAuthn (passkeys), Auth0 java-jwt |
| Push | Firebase Admin SDK |
| Testing | JUnit 5, MockK, H2 for datasource tests |
| Build & ship | Gradle (convention plugins, configuration cache), Jib → Docker, Docker Swarm |
| Observability | Micrometer/Prometheus, Grafana, Alertmanager |

---

## Getting started

### Prerequisites

- **JDK 21** (Temurin recommended)
- **Docker** with Compose v2
- Roughly 4 GB of free RAM for the local stack

### Build and test

```sh
./gradlew build          # compile everything
./gradlew test           # run the full test suite
```

Scope work to a single module while iterating:

```sh
./gradlew :service:appointments:domain:impl:test
./gradlew :service:appointments:microservice:test --tests "com.bookk.appointments.*.CreateAppointmentTest"
```

---

## Running the local stack

`deployment/dev/rollout.sh` brings up infrastructure, builds each service image with Jib, and starts the services:

```sh
cd deployment/dev
./rollout.sh
```

That script does three things, which you can also run separately:

1. `docker compose -f environment-compose.yml up -d` — MariaDB, Kafka, Redis, Keycloak, nginx
2. `./gradlew :service:<svc>:microservice:publishImageToLocalRegistry` for each service
3. `docker compose -f microservices-compose.yml up -d` — the five services

Services are published on `localhost:8000`–`8004`, and each container also exposes a **JDWP debug agent** on its internal port 8000, so you can attach a remote debugger from your IDE.

> **Note:** the Gradle daemon sometimes fails to resolve Docker from the environment. `rollout.sh` runs `./gradlew --stop` first for exactly this reason — if image builds fail with a Docker-not-found error, stop the daemon and retry.

---

## Configuration

Services are configured entirely through environment variables. Per-service `.env` files for local development live in `deployment/dev/service/`.

| Variable | Purpose |
|---|---|
| `APPLICATION_SERVICE_NAME` / `_VERSION` / `_HOSTNAME` / `_PORT` | Service identity and bind address |
| `APPLICATION_DOMAIN_NAME` | Public domain, used for WebAuthn relying-party ID and links |
| `APPLICATION_DB_SCHEME` / `_URL` / `_PORT` / `_USER` / `_PASSWORD` | Database connection |
| `APPLICATION_REDIS_HOSTS` / `_PORT` / `_PASSWORD` | Cache connection |
| `APPLICATION_KAFKA_HOSTS` / `_CLUSTER_ID` | Event streaming (`HOSTS` is comma-separated) |
| `APPLICATION_<OTHER>_SERVICE_HOSTNAME` | Service-to-service addressing |

Secrets that are not environment variables are mounted as Docker secrets: `signing_key_encryption_key` (auth and business) and `firebase-private-key.json` (notifications).

`local.properties` at the repo root holds your Docker registry credentials (`DOCKER_USERNAME`, `DOCKER_PASSWORD`) for image publishing and is not tracked.

---

## Testing

The suite is fast and hermetic — no Testcontainers, no shared fixtures between tests.

- **Operation tests** (`domain/impl`) mock datasources with MockK and run under `runUnitTest { }`.
- **Route tests** (`microservice`) spin up a Ktor test application via `routeTest { }` and assert status codes and error payloads.
- **Datasource tests** (`data`) run against a per-test in-memory H2 database in MySQL compatibility mode.

Two conventions the fixtures enforce at runtime:

- every test calls the `given()` / `whenn()` / `then()` markers — a test missing one fails;
- every test builds its subject through a private `SutFixture` class, so mocks are never shared across tests.

`runBlocking` is banned in tests; use the provided coroutine-aware runners.

---

## Database migrations

Each service has a `<Svc>Migration.kt` that diffs the Exposed table definitions against a reference schema and generates a Flyway SQL script.

**Migrations are generated deliberately, never automatically.** Changing a table definition does not produce a migration — a maintainer runs the generator and reviews the SQL before it lands. New tables must be added to the service's `tables()` array or they will be silently excluded.

---

## Deployment

Production runs the **monolith** on **Docker Swarm**, next to separate infrastructure stacks (`deployment/prod/`): VPN (WireGuard), nginx, orchestrator (Dozzle), monitoring (Prometheus + Grafana + Alertmanager) and the admin panel. CI runs on GitHub-hosted runners, nothing of it lives in the swarm. Everything is deployed with Ansible (`deployment/ansible/`, see its README): `make bootstrap` / `make infra` for hosts, swarm and infrastructure, `make deploy v=<version>` for the monolith.

GitHub Actions only builds and publishes: `push-build-and-test.yml` builds and tests every push and pull request, and pushing a `monolith/<version>` tag runs `monolith-publish.yml`, which publishes `ghcr.io/nocturneprops/com.bookk.server:<version>` with Jib and then deploys it through `deploy.yml` (the same Ansible playbook, gated by the `production` environment). The microservice images are still published for when the services are split out again: a `auth/`, `user/`, `business/`, `appointment/` or `notification/<version>` tag runs `microservice-publish.yml`, which publishes `ghcr.io/nocturneprops/com.bookk.server.microservice.<service>:<version>` and deploys nothing.

---

## Contributing

**[`AGENTS.md`](AGENTS.md) is the authoritative style guide** — it documents the recipes for adding an operation, a route, a datasource, and their tests, with the reasoning behind each rule. Read it before your first change. Highlights:

- **Development is test-driven.** Write the failing test first, then the minimum implementation, then refactor.
- **No comments in production code or tests.** Express intent through names and structure. The only exception is route KDoc, which the OpenAPI plugin parses.
- New Gradle modules must be registered in `settings.gradle.kts`.
- Follow `service/appointments` as the reference implementation — it is the newest service and the most consistent with current conventions.

---

## License

Bookk is **source-available** under the **Functional Source License, Version 1.1, ALv2 Future License** (`FSL-1.1-ALv2`) — see [`LICENSE`](LICENSE) — plus an [additional permission for self-hosting](LICENSE-SELF-HOSTING.md).

- **Allowed:** reading, modifying and running the code for any purpose other than a competing product — internal use, non-commercial education and research, contributing, and self-hosting Bookk to take bookings for your own business, with your own clients booking through it.
- **Not allowed:** offering Bookk, or a product built from it, to other businesses — as a hosted service, as software you sell, or as part of a booking platform.
- **Becomes Apache 2.0 after two years:** each version is also licensed under the Apache License, Version 2.0 from the second anniversary of its release.
- **Earlier versions stay Apache 2.0:** versions published before the switch to FSL — every commit whose `LICENSE` file is the Apache License — were released under the Apache License, Version 2.0 and remain available under it.
