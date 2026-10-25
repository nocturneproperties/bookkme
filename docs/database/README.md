# Database ER diagrams

bookk-server is a modular monolith split into independently deployable Ktor
microservices. Each microservice owns its own tables (there is no shared
database and no real cross-service foreign key) — cross-service links are
**logical** references (a plain `uuid` column that happens to hold the id
of a row owned by another service) and are drawn as dashed relationships in
the [cross-service overview](cross-service-overview.md).

One Mermaid ER diagram per microservice, generated from the Exposed table
definitions under `service/<svc>/data/src/main/kotlin/.../orm/table/`.

- [Authorization service](authorization.md)
- [User service](user.md)
- [Business service](business.md)
- [Appointments service](appointments.md)
- [Notifications service](notifications.md)
- [Cross-service overview](cross-service-overview.md)

All tables inherit an `id UUID PK`. Tables extending the shared
`BaseUUIDTable` additionally get `createdAt` / `updatedAt` (nullable); tables
built directly on Exposed's `UuidTable` declare their own timestamp columns
where present — both are shown per table in each service's diagram.
