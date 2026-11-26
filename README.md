# BookkMe Server

**BookkMe** is an appointment-booking platform for small businesses such as salons, barbers, studios, clinics and tutors. Clients find a business, pick a service and a time, and get notified about their booking. The business manages its services, staff, working hours, clients and bookings in one place.

This repository holds the BookkMe backend. You can run it yourself to take bookings for your own business, study how it works, or change it to fit your needs, within the terms of the [license](#license).

---

## Contents

- [Features](#features)
- [Try it locally](#try-it-locally)
- [Self-hosting for your business](#self-hosting-for-your-business)
- [Configuration](#configuration)
  - [Telegram sign-in](#telegram-sign-in)
  - [Demo accounts](#demo-accounts)
  - [Creation limits](#creation-limits)
- [Running as microservices](#running-as-microservices)
- [How the services communicate](#how-the-services-communicate)
- [Security](#security)
- [Privacy and GDPR](#privacy-and-gdpr)
- [Upgrading](#upgrading)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [Support](#support)
- [Feature requests](#feature-requests)
- [Contributing](#contributing)
- [License](#license)

---

## Features

**For businesses**

- Services and service groups, each with its own price and duration
- Staff accounts with invite codes and separate view, edit and delete permissions for business details, staff, clients, services and appointments, plus suspending access
- Working hours, days off and schedules for every employee: a booking must fit both the business's hours and the employee's, a range ending before its start (such as 20:00 to 00:00) runs past midnight, a range can't start and end at the same time, and new employees (the owner included) start on the business's working days and hours
- A client list with import, notes, search by the start of a first or last name, by email or by phone, and automatic matching to client accounts
- Booking requests you approve or decline, or instant booking when you prefer; clients can withdraw a request you haven't answered
- A booking link with a name the owner picks, such as `bookkme.com/my-salon`; a replaced name keeps leading to the business for 90 days and no other business can take it meanwhile
- Appointment and booking request history, filtered by client, staff member, status and dates, with completion and no-show tracking

**For clients**

- Passwordless sign-in with passkeys, with the email verified by a one-time code
- Sign-in from a Telegram Mini App, which creates the account on first use (see [Telegram sign-in](#telegram-sign-in))
- 14-day demo accounts with a sample business, started with one tap and kept by adding an email and a passkey (see [Demo accounts](#demo-accounts))
- Booking, rescheduling and cancelling appointments, with the business's note for clients shown before booking and a choice of staff member showing each one's services, working hours and free time slots; a staff member can't be booked twice for the same time
- A home screen with the nearest upcoming bookings across every business, and saved businesses to book again with the date of the last visit
- A client's own history of appointments and booking requests across every business, split into upcoming and past
- Push, email and Telegram notifications, with per-channel settings
- Notifications in the language of the client's device

**For operators**

- Runs as a single server process backed by MariaDB and Valkey (or Redis)
- Database migrations run automatically at startup
- Limits on how many staff, clients, services, businesses, pending booking requests, saved businesses and passkeys can be created, which you can raise for a single business or person (see [Creation limits](#creation-limits))
- Personal data encrypted with a key per person, so erasing someone also makes their data unreadable in backups
- Personal-data tools for GDPR: data export, account and client erasure, dormant account clean-up and retention rules (see [Privacy and GDPR](#privacy-and-gdpr))
- Draft privacy policy and terms of service templates for self-hosting businesses in English, Polish and Ukrainian (`docs/legal/template`)
- Prometheus metrics and structured logs, with alerts for signs of misuse: sign-in and email code abuse, reused refresh tokens, probing of other businesses' data, unusual export and response volumes, failed database and cache logins, admin panel and SSH logins, and deleted backups
- An OpenAPI description of the whole API, served with Swagger UI

---

## Try it locally

You need **Docker** with Compose v2, **JDK 21** and about 4 GB of free memory.

```sh
git clone https://github.com/nocturneproperties/bookkme.git
cd bookkme/deployment/dev
./rollout-monolith.sh
```

The script starts MariaDB, Valkey and nginx, builds the server image and starts it. When it finishes:

- the API answers on `http://localhost:8085/api/`
- Swagger UI with every route is at `http://localhost:8085/api/monolith/internal/swagger`

The development setup does not send email. It writes every message to the server log instead, verification codes included, so you can sign up without a mail provider. Push notifications need your own Firebase key in `deployment/dev/security/firebase-private-key.json`. Telegram notifications need your bot's token as `APPLICATION_TELEGRAM_BOT_TOKEN=<token>` in `deployment/dev/.env`, which git ignores.

To stop everything:

```sh
docker compose -f monolith-compose.yml down
docker compose -f environment-compose.yml down
```

---

## Self-hosting for your business

The [self-hosting permission](LICENSE-SELF-HOSTING.md) lets you run BookkMe to take bookings for your own business, with your staff and your clients using it, and lets a hosting provider or IT contractor run it for you. It does not let you offer BookkMe to other businesses.

### What you need

- A Linux server with Docker
- **MariaDB 11.8** or newer
- **Valkey 8** or a compatible Redis
- A domain with TLS, and a reverse proxy in front of the server (an nginx example is in `deployment/dev/nginx-monolith.conf`)
- A [Hostinger](https://www.hostinger.com/mail-api) mailbox with Mail API access, for verification codes and email notifications. Emails go out from that mailbox's address. Hostinger keeps a copy of each one in the mailbox's Sent folder, and the server deletes copies older than 5 years once a week
- A Firebase project, for push notifications

### Steps

1. **Build the image.** `./gradlew publishImageToLocalRegistry` builds `bookkme-server` into your local Docker. Release images are also published as `ghcr.io/nocturneproperties/bookkme-server:<version>`.
2. **Create the databases.** The server keeps its data in five schemas (`authorization`, `user`, `business`, `appointments`, `notifications`) and each person's encryption key in four more (`user_short_retention`, `business_short_retention`, `appointments_short_retention`, `notifications_short_retention`). It needs one user with full rights on all nine. Create the first five yourself; the server creates the key schemas and every table on first start, but its user must already have the rights on them. `deployment/dev/db/init.sql` shows the statements.
3. **Configure it.** Set the [environment variables](#configuration) and mount the two secret files.
4. **Start it** behind your reverse proxy, on HTTPS. Passkeys only work on the domain in `APPLICATION_DOMAIN_NAME`.
5. **Publish your legal texts.** Fill in the templates in `docs/legal/template` for your business, have them reviewed, and publish them as described in [`docs/legal-documents.md`](docs/legal-documents.md).

`deployment/prod` and `deployment/ansible` contain the production setup we run ourselves on Docker Swarm, with backups, monitoring and a VPN. Use them as a reference; they are written for our own infrastructure and will need adapting.

---

## Configuration

The server is configured through environment variables. `deployment/dev/service/monolith.env` is a working example for local use; never reuse its passwords and secrets in production.

| Variable | Purpose |
|---|---|
| `APPLICATION_DOMAIN_NAME` | Your public domain, used for passkeys and links |
| `APPLICATION_SITE_DOMAIN_NAME` | Optional. A website on another domain whose pages may use the same passkeys (it, its `www.` and the web app on its `app.`); the domain in `APPLICATION_DOMAIN_NAME` must then serve `/.well-known/webauthn` listing those origins. Leave it unset if passkeys are used only in the apps and on your public domain |
| `APPLICATION_PASSKEY_EXTRA_ORIGINS` | Optional. More `https://` origins, separated by commas, whose pages may use the passkeys, such as a development host under your domain. Leave it unset in production |
| `APPLICATION_SERVICE_NAME` | `monolith` |
| `APPLICATION_SERVICE_HOSTNAME`, `APPLICATION_SERVICE_PORT` | Address the server binds to |
| `APPLICATION_SERVICE_VERSION` | Version shown in the API description |
| `APPLICATION_LOG_LEVEL` | `info`, `debug`, … |
| `APPLICATION_DB_URL`, `APPLICATION_DB_PORT`, `APPLICATION_DB_USER`, `APPLICATION_DB_PASSWORD` | Database connection |
| `APPLICATION_REDIS_HOSTS`, `APPLICATION_REDIS_PORT`, `APPLICATION_REDIS_PASSWORD` | Cache connection |
| `APPLICATION_AUTH_SERVICE_HOSTNAME`, `…_USER_…`, `…_BUSINESS_…`, `…_APPOINTMENTS_…`, `…_NOTIFICATIONS_…` | Internal addresses; in a single-server setup all point at the server itself |
| `APPLICATION_HOSTINGER_MAIL_TOKEN` | Hostinger Mail API token allowed to send from your mailbox; `development_token` logs emails instead of sending them |
| `APPLICATION_HOSTINGER_MAILBOX_ID` | Resource ID (`AC…`) of the mailbox emails are sent from; not needed with `development_token` |
| `APPLICATION_EMAIL_SENDER_NAME` | Sender name shown next to the mailbox address, such as your business name |
| `APPLICATION_EMAIL_CODE_SECRET` | Random secret of at least 32 bytes for verification codes |
| `APPLICATION_SUBJECT_MASTER_KEY` | Base64 of exactly 32 random bytes; wraps every person's encryption key. Keep it outside the database and never lose it: without it no encrypted field or export can be read |
| `APPLICATION_BLIND_INDEX_KEY` | Base64 of at least 32 random bytes, for the keyed hashes that look up encrypted names, emails and phones and Telegram accounts |
| `APPLICATION_TELEGRAM_BOT_ID` | Optional. The numeric id of the Telegram bot whose Mini App signs users in (the part of its token before the colon). Leave it unset to turn Telegram sign-in off |
| `APPLICATION_TELEGRAM_BOT_TOKEN` | Optional, a secret. The token of that same bot, used only to send Telegram notifications and confirmation links. Leave it unset and nothing is sent to Telegram. Set it whenever Telegram sign-in is on, or accounts created through Telegram get no warning before a dormant account is deleted and can't delete their account or export their data |
| `APPLICATION_TELEGRAM_BOT_USERNAME` | Optional. That bot's username, without the `@`, for the confirmation links the bot sends, which open the bot's main Mini App (set BookkMe as it in BotFather). Set it whenever Telegram sign-in is on, or accounts created through Telegram can't delete their account or export their data |

Two secrets are read from files, as Docker secrets:

| Secret | Purpose |
|---|---|
| `signing_key_encryption_key` | Encrypts the keys the server signs access tokens with |
| `firebase_key` | Firebase service account key, for push notifications |

### Telegram sign-in

A Telegram Mini App can sign its users in with `POST /api/auth/telegram/sign_in`, sending the Mini App's `initData` as it received it. The server checks Telegram's signature with Telegram's public key and your bot id, so sign-in never needs the bot token, and accepts the data for one hour after Telegram issued it. Set `APPLICATION_TELEGRAM_BOT_ID` to turn it on.

The first sign-in of a Telegram user creates an account with their Telegram first and last name, after they accept the privacy policy and terms like any new user. Telegram doesn't share an email address, so the account gets the placeholder `<telegram user id>@telegramundeliverable.org`. The server never sends email to it: no notification emails or codes. The user can switch to a real address through the usual email change, and can add a passkey. They confirm deleting their account or exporting their data through Telegram, with or without a passkey, since the Mini App can't use one: the bot sends them a one-time button that opens the Mini App, valid for 5 minutes, and the server accepts it only from the Telegram account linked to theirs. Once they confirm, the bot changes that message to say so and removes the button; starting a new confirmation deletes the previous one if it was never confirmed. Deleting a business's client or exporting a business still needs a passkey. Telegram sign-in itself keeps the Telegram user id only as a keyed hash; the account's profile and its notification settings keep it encrypted under the user's own key, as the chat the bot writes notifications to, so deleting the account makes it unreadable. The user's Telegram username, when they have one, is kept encrypted in the profile too and refreshed on every Telegram sign-in, so a changed or removed username replaces the old one. When the user books with a business, the business's client record shows that username, so staff can contact the client on Telegram, and it follows later changes. Nothing else from Telegram is stored except the name.

With `APPLICATION_TELEGRAM_BOT_TOKEN` set, the bot sends notifications to these users' private chats as plain text, in the language of the device they last signed in on. Telegram appears in their notification settings, turned on when the account is created. A bot can only write to someone who allowed it, so the app asks for that permission (the Mini App's `requestWriteAccess()`) right after sign-in and before turning the channel back on; if the user never allows it, the first notification the bot can't deliver turns the channel off. Account notifications, such as the warnings before a dormant account is deleted and a finished data export, go to Telegram even with the channel off while the account has no real email address. If the user blocks the bot, the server turns their Telegram channel off. Telegram receives the text of each notification it delivers, such as a business name and appointment time, so list it among your providers.

### Demo accounts

Someone who isn't ready to sign up can start a demo with `POST /api/auth/demo`: one request creates an account without an email address, signs the device in and, within moments, fills a sample business for it with services, ten fictional clients and about a week of bookings around today. The demo lasts 14 days. Its business is marked as a demo, so the app can show a badge, and gets a random `demo-…` link; nobody can pick a link starting with `demo-`, and demo accounts can't pick a link at all. Demo accounts can do everything else, invite staff and join other businesses included, but they can only book at businesses they belong to, so they can't be used to flood real businesses with requests. A demo account can own one business.

The device's sign-in is the demo's only credential: signing out or reinstalling the app ends it for good. To keep the account and everything in it, the user adds an email address, confirmed with a code, and a passkey, while the demo runs or within 7 days after it ended. A demo that was not kept is deleted 7 days after it ended, like a deleted account, and its businesses and bookings with it.

Demos need no setup. Creation is anonymous, so the server allows at most 1,000 demos running at once and refuses more with a 429 error until older ones end; the bundled nginx configuration also lets each address start one demo a minute (bursts of 3), so keep a similar limit on `POST /api/auth/demo` if you use another reverse proxy.

### Creation limits

The server caps how much one business or one person can create. A request over a limit is refused with a 422 error naming the limit; a client import creates clients up to the limit and reports the rest as rejected.

| Limit | Default | Applies to | Raise it with |
|---|---|---|---|
| Active staff, the owner included (suspended staff don't count) | 100 | each business | `business.business.max_employees` |
| Clients | 20,000 | each business | `business.business.max_clients` |
| Services | 300 | each business | `business.business.max_services` |
| Service groups | 50 | each business | `business.business.max_service_groups` |
| Businesses a person owns (working at others doesn't count) | 20, 1 for a demo account | each person | `business.user_limits.max_businesses` (not for demo accounts) |
| Pending booking requests one person has at a business | 10 | each business | `appointments.appointment_settings.max_pending_requests_per_client` |
| Saved businesses | 100 | each account | fixed |
| Passkeys | 10 | each account | fixed |

Every column is empty by default, which means the default value. Set it to raise or lower the limit for one business or person, for example `UPDATE business.business SET max_clients = 50000 WHERE id = UNHEX(REPLACE('<business id>', '-', ''));`, and set it back to `NULL` to return to the default. A person gets their `business.user_limits` row the first time they create a business; for someone who has none yet, insert it (`INSERT INTO business.user_limits (user_id, max_businesses, created_at) VALUES (UNHEX(REPLACE('<user id>', '-', '')), 40, UTC_TIMESTAMP(6));`). Clients who book through the app are never refused, so a business can go past its client limit that way. Reinstating a suspended employee is refused while the business is full, and a client frees a place by withdrawing a pending request.

---

## Running as microservices

BookkMe is made of five services:

| Service | Responsibility |
|---|---|
| `auth` | Accounts, sign-up and email changes with email verification, passkey and Telegram sign-in, access tokens, devices |
| `user` | Profiles, legal document acceptance, data export |
| `business` | Businesses, services, staff and invitations, permissions, clients, price quotes |
| `appointments` | Booking requests, approvals, appointments, schedules and history |
| `notifications` | Push, email and Telegram notifications, device tokens, notification settings |

The same code runs in two ways:

- **As one server** (the default above). `MonolithServer` starts all five services in one process, and they pass events to each other in memory. You only need MariaDB and Valkey. This is what we run in production, and it is the right choice for a single business.
- **As five separate services.** Each service has its own entry point, its own image and its own database user that can only reach its own schema. The services send events to each other through **Apache Kafka** and call each other over internal HTTP. Choose this when you need to scale or deploy the services independently.

### Try it locally

```sh
cd deployment/dev
./rollout.sh
```

The script starts MariaDB, Valkey, Kafka (with Kafka UI on `http://localhost:8080`) and nginx, builds an image for each service and starts them. nginx on `http://localhost` routes `/api/auth`, `/api/user`, `/api/business`, `/api/appointments` and `/api/notifications` to the matching service, and each service serves its own Swagger UI, for example `http://localhost/api/appointments/internal/swagger`. Ports `8000`–`8004` are Java debugger ports, one per service, so you can attach your IDE.

### Configuration

Each service takes the variables from [Configuration](#configuration), with its own values; `deployment/dev/service/<service>.env` has an example for each. The differences from the single server:

| Variable | Purpose |
|---|---|
| `APPLICATION_SERVICE_NAME` | `auth`, `user`, `business`, `appointments` or `notifications`; it also sets the route prefix `/api/<name>` |
| `APPLICATION_DB_USER`, `APPLICATION_DB_PASSWORD` | The service's own database user, with rights on its own schema and, for every service but `auth`, on its `<service>_short_retention` key schema |
| `APPLICATION_KAFKA_HOSTS` | Kafka bootstrap servers, separated by commas |
| `APPLICATION_KAFKA_CLUSTER_ID` | Kafka cluster id |
| `APPLICATION_<SERVICE>_SERVICE_HOSTNAME` | Where to reach the other services it calls |

Mount `signing_key_encryption_key` into `auth` and `business`, which sign tokens, and `firebase_key` into `notifications`. `auth` and `notifications` also need the email settings, `notifications` needs `APPLICATION_TELEGRAM_BOT_TOKEN` and `APPLICATION_TELEGRAM_BOT_USERNAME` for Telegram notifications and confirmation links, and `auth` needs `APPLICATION_BLIND_INDEX_KEY` (the same value as the other services) for Telegram sign-in and `APPLICATION_NOTIFICATIONS_SERVICE_HOSTNAME` to have the bot send confirmation links.

### Images

`./gradlew :service:<service>:microservice:publishImageToLocalRegistry` builds one service's image (`<service>` is `authorization`, `user`, `business`, `appointments` or `notifications`). Release images are published as `ghcr.io/nocturneproperties/bookkme-<service>:<version>`. `deployment/prod/microservice` and `deployment/prod/kafka` show a Docker Swarm setup for this mode.

---

## How the services communicate

Each service owns its data and never reads another service's database. They work together in two ways:

- **Events** for anything that happened. A service announces a change, such as `appointment.request_created`, `business.client_deleted` or `auth.user_deleted`, and every service that cares reacts to it in its own time. Notifications, for example, reacts to booking requests, approvals and cancellations by sending pushes, emails and Telegram messages, and every service deletes its own data when it receives `auth.user_deleted`.
- **Internal HTTP calls** for data a service needs right away, such as the booking flow asking `business` for a service's price and duration.

Events are reliable:

- **Transactional outbox.** A service saves an event in its own `outbox_event` table in the same database transaction as the change it describes. A background relay publishes saved events afterwards. A failed change never produces an event, and an event is not lost when Kafka is down; it waits in the outbox.
- **At-least-once delivery, handled once.** An event can arrive more than once, after a crash or a restart, so each consumer records which events it has handled in Valkey and skips the repeats.
- **Retries and parking.** A failed handler is retried through a retry topic of its own. After the last attempt the event is parked in an `exhausted_event` table and raises a metric for alerts. Parked events are replayed on the next start, or when an operator asks for it, and are deleted after 30 days.

The single server uses exactly the same producers, outbox and consumers, with an in-memory transport in place of Kafka. Events are encoded as ProtoBuf and described by the `client` module of the service that publishes them. [`docs/transactional-outbox.md`](docs/transactional-outbox.md) and [`docs/exhausted-events.md`](docs/exhausted-events.md) go into detail.

---

## Security

**Sign-in**

- **No passwords.** Accounts sign in with passkeys (WebAuthn), so there is no password to steal, reuse or phish.
- **Telegram sign-in checked against Telegram's signature.** Only Mini App data signed by Telegram for your bot and less than an hour old signs a user in. Checking it needs no bot token; the token is only used to send notifications and never appears in the logs.
- **Verified email.** An account is created only after its owner enters a 6-digit code sent to the address, and the address changes only through a code sent to the new one. Accounts created through Telegram have a placeholder address that is never emailed, and nobody can sign up with or switch to an address on that placeholder domain. Codes expire after 10 minutes, allow a limited number of attempts and resends, and are stored only as keyed hashes.
- **Short-lived tokens.** Access tokens live 5 minutes. Refresh tokens belong to one device and are replaced on every use; an old refresh token that comes back signs its device out and raises a metric for alerts.
- **No notifications after sign-out.** Signing out, or being signed out by a reused refresh token, removes the device's push token, so a shared or handed-over phone stops showing the previous user's notifications. The app sends its token again after the next sign-in. A session that just runs out, after 7 days without opening the app, keeps its push token until the device is removed 30 days after its last sign-in, so sign out before handing a phone over.
- **Rotating signing keys.** Token signing keys rotate daily and are published as a JWKS, so old keys stop being trusted on their own. The private keys are stored encrypted. Every token type has its own audience, so one kind of token cannot be used as another.
- **Confirmation for sensitive actions.** Deleting an account, deleting a client and exporting personal data each need a fresh passkey confirmation, not only a signed-in session.

**Access control**

- **Fine-grained permissions.** The owner gives each staff member separate view, edit and delete rights per area, and can suspend a staff member's access at once. Every request is checked on the server.
- **No probing.** A resource the caller may not see answers 404, the same as one that does not exist, so its existence is not revealed.
- **Business boundaries.** Every write is limited to the business the caller's permission was checked against, so an id from another business does nothing.
- **Server-side data.** Prices, durations, names and contact details are looked up on the server from their ids, never taken from what the app sends.

**Abuse and replay protection**

- **Idempotency keys** on requests, so a retried request never takes effect twice.
- **Single-use tokens** that are claimed atomically, so two concurrent requests cannot both use one.
- **Rate limits** in the nginx configuration for sending and checking email codes, passkey sign-in and creating demo accounts.
- **Creation limits** on staff, clients, services, businesses, pending booking requests, saved businesses and passkeys, so one account can't fill the database (see [Creation limits](#creation-limits)).

**Infrastructure** (the production setup in `deployment/`)

- The database refuses unencrypted connections, and the server verifies its certificate.
- Database tables, logs and temporary files are encrypted at rest.
- The database and cache are reachable only from a private network, not from the reverse proxy or the internet, and traffic between hosts is encrypted.
- Backups are encrypted with a public key before they leave the server, and kept for 30 days. The per-person encryption keys are left out of them and backed up separately every 5 minutes, kept for 7 days.
- Secrets are passed as Docker secrets from an encrypted Ansible vault, and nothing from the vault stays on the server's disk.

Found a vulnerability? See [Support](#support) for how to report it privately.

---

## Privacy and GDPR

BookkMe is built to help whoever runs it meet the GDPR. It handles personal data with these tools:

- **Right of access and portability.** Users can export all their personal data from every service, after confirming with their passkey.
- **Right to erasure.** Deleting an account erases the user's data in every service, cancels their upcoming appointments and removes their personal details from the appointment history. The erasure runs 7 days after the request: the account keeps working until then, the user is told at once and reminded 2 days and 1 day before, and can cancel in the app. A business can delete any of its clients in the same way.
- **Storage limitation.** An account unused for 3 years is deleted after three warnings by email (on Telegram for an account created through Telegram with no real address). A demo account that was not kept is deleted 7 days after its 14 days end. Each business sets how long it keeps inactive clients (12 to 120 months, 36 by default), and older ones are deleted automatically. Booking requests are deleted 60 days after their time slot, and failed events after 30 days.
- **Encryption per person.** Each person's data is encrypted with a key of their own: profiles, client and staff details, appointment snapshots and notes, stored events and data exports. Erasing a person deletes their key, so every copy of their data left in the database or in a backup can no longer be read once the backups of that key have expired. The keys live in their own schemas, which never enter the binary log, so they can be backed up on a shorter schedule than the data (we keep them 7 days). Back them up: without its key, a person's data cannot be recovered.
- **Erasure survives backups.** Every erasure is recorded, and restoring an older backup runs the erasures made since then again and deletes the keys erased since then, so a deleted person does not come back.
- **Recorded acceptance.** The server records which version of the privacy policy and terms each user accepted.
- **Data minimisation.** Staff see only what their permissions allow, clients see a staff member's name, services and working hours but never their contact details or permissions, and notification emails go only to verified addresses, never to the placeholder of an account created through Telegram.
- **Policy templates.** `docs/legal/template` contains a draft privacy policy and terms of service for a business running its own copy, in English, Polish and Ukrainian.

The software alone does not make a deployment compliant. When you self-host BookkMe, your business is the data controller: you decide the legal basis, publish your own privacy policy and terms (the templates are drafts that need review for your country), sign data processing agreements with your providers (such as your hosting, email, push notification and Telegram providers), and answer requests from the people whose data you hold. [`docs/legal/README.md`](docs/legal/README.md) lists what to complete before publishing.

---

## Upgrading

Every release is described in [`RELEASE_NOTES.md`](RELEASE_NOTES.md): what changed for apps and operators, new settings, and anything that can stop an upgrade. Read the entries between your version and the new one before upgrading, and back up the database. Migrations run when the new version starts.

Versions follow semantic versioning. While the version is `0.x`, a minor release can contain breaking changes.

---

## Building from source

```sh
./gradlew build                                   # compile and test everything
./gradlew test                                    # run the tests
./gradlew :service:appointments:domain:impl:test  # test one module
./gradlew spotlessApply                           # format the code
```

Some tests start MariaDB, Valkey and Kafka with Testcontainers, so `./gradlew test` needs Docker running.

The server is written in Kotlin with Ktor, Koin, Exposed and Flyway, and speaks ProtoBuf (`application/x-protobuf`) rather than JSON.

---

## Project layout

```
src/            the single-server entry point that runs every service in one process
service/        authorization, user, business, appointments, notifications
core/           shared server, database, event and cache code
library/        money, permissions, validation, email, scheduling and other building blocks
build-src/      Gradle convention plugins
deployment/     local development stack and our production setup
docs/           database diagrams, operation diagrams, legal texts and design notes
```

Each service can also run as its own process, talking to the others over Kafka (see [Running as microservices](#running-as-microservices)). [`AGENTS.md`](AGENTS.md) and `docs/agents/` describe the code conventions in detail.

---

## Support

BookkMe is maintained by a small team, and support is best effort. [GitHub issues](https://github.com/nocturneproperties/bookkme/issues) are for bugs only: include the version you run and the steps that reproduce the problem. Please do not report security vulnerabilities in a public issue; use GitHub's private vulnerability reporting for this repository instead.

---

## Feature requests

Suggest new features and improvements in [GitHub Discussions](https://github.com/nocturneproperties/bookkme/discussions), not in issues. Describe the problem you want solved, not only the solution, so others can add their use cases and vote on it. **Feature requests opened as issues will be closed.**

---

## Contributing

**Unfortunately, we do not accept contributions.** Pull requests will be closed without review. This keeps the code, its licensing and its legal texts under one owner.

You are welcome to fork the project and change your copy within the terms of the license. Bug reports in issues and ideas in Discussions are always appreciated.

---

## License

BookkMe is **source-available** under the **Functional Source License, Version 1.1, ALv2 Future License** (`FSL-1.1-ALv2`) — see [`LICENSE`](LICENSE) — plus an [additional permission for self-hosting](LICENSE-SELF-HOSTING.md).

- **Allowed:** reading, modifying and running the code for any purpose other than a competing product — internal use, non-commercial education and research, and self-hosting BookkMe to take bookings for your own business, with your own clients booking through it.
- **Not allowed:** offering BookkMe, or a product built from it, to other businesses — as a hosted service, as software you sell, or as part of a booking platform.
- **Becomes Apache 2.0 after two years:** each version is also licensed under the Apache License, Version 2.0 from the second anniversary of its release.
- **Earlier versions stay Apache 2.0:** versions published before the switch to FSL — every commit whose `LICENSE` file is the Apache License — were released under the Apache License, Version 2.0 and remain available under it.
