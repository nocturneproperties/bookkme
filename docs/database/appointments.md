[← Database ER diagrams](README.md)

# Appointments service

Keeps its own denormalized copy of business identity (`business_has_appointments`,
populated from the business service via events) so booking logic never has to
cross a service boundary at read time. `APPOINTMENT`/`APPOINTMENT_REQUEST` also
denormalize employee, client and service line items (`employee_name`,
`client_name`/`phone`/`email`, `service_name`/`price`/`duration`, …) the same
way, but these are populated by resolving the ids synchronously from the
business service at write time (`GetAppointmentBookingContext`, see
[create-appointment-request](../operations/appointments/create-appointment-request.md))
rather than replicated via events — the client only ever supplies the ids.
The same holds for the additional services of an `APPOINTMENT_PRICE_ADJUSTMENT`
(resolved through `GetServicesByIds` when an appointment is completed, see
[complete-appointment](../operations/appointments/complete-appointment.md)).
Both adjustment tables cascade-delete with their appointment.

```mermaid
erDiagram
    BUSINESS_HAS_APPOINTMENTS ||--o{ APPOINTMENT : "hosts"
    BUSINESS_HAS_APPOINTMENTS ||--o{ APPOINTMENT_REQUEST : "hosts"
    BUSINESS_HAS_APPOINTMENTS ||--o{ APPOINTMENT_DAY_OFFS : "has"
    BUSINESS_HAS_APPOINTMENTS ||--|| APPOINTMENT_SETTINGS : "configures"
    BUSINESS_HAS_APPOINTMENTS ||--o{ WORKING_HOURS : "has"
    BUSINESS_HAS_APPOINTMENTS ||--o{ APPOINTMENT_PERMISSION_GRANTS : "grants"
    APPOINTMENT ||--o{ APPOINTMENT_SERVICES : "line items"
    APPOINTMENT ||--o| APPOINTMENT_PRICE_ADJUSTMENT : "checkout price"
    APPOINTMENT_PRICE_ADJUSTMENT ||--o{ APPOINTMENT_PRICE_ADJUSTMENT_SERVICES : "additional services"
    APPOINTMENT_REQUEST ||--o{ APPOINTMENT_REQUEST_SERVICES : "line items"

    BUSINESS_HAS_APPOINTMENTS {
        uuid id PK "= business.id (replicated)"
        bool enabled
        string name
        string address
        string time_zone
        byte working_days
        timestamp source_updated_at
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT {
        uuid id PK
        uuid user_id "logical FK -> user.profile.id; non-unique index with business_id, date_start (overlap checks)"
        uuid business_id FK
        uuid employee_id "logical FK -> business.employee.id"
        uuid employee_user_id "logical FK -> user.profile.id (employee's own userId)"
        string employee_name
        uuid client_id "logical FK -> business.client.id"
        string client_name
        string client_phone
        string client_email
        timestamp date_start
        timestamp date_end
        string note
        enum status
        string cancellation_reason
        enum completed_by "nullable; SYSTEM (MarkAppointmentsCompleted job) or USER (CompleteAppointment); set only while status = COMPLETED"
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_SERVICES {
        uuid id PK
        uuid appointment_id FK
        uuid service_id "logical FK -> business.service.id"
        string service_name
        uuid service_group_id "logical FK -> business.service_group.id"
        string price_currency
        long price_unscaled
        int price_scale
        long duration_minutes
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_PRICE_ADJUSTMENT {
        uuid id PK
        uuid appointment_id FK,UK "at most one per appointment; replaced on every CompleteAppointment carrying an adjustment"
        string price_currency "must equal the appointment services currency"
        long price_unscaled "final charged price, replaces the sum of booked and additional services"
        int price_scale
        string reason "nullable"
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_PRICE_ADJUSTMENT_SERVICES {
        uuid id PK
        uuid price_adjustment_id FK
        uuid service_id "logical FK -> business.service.id"
        string service_name
        uuid service_group_id "logical FK -> business.service_group.id"
        string price_currency
        long price_unscaled
        int price_scale
        long duration_minutes
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_REQUEST {
        uuid id PK
        uuid user_id "logical FK -> user.profile.id; non-unique index with business_id, date_start (overlap checks)"
        uuid business_id FK
        uuid employee_id "logical FK -> business.employee.id"
        uuid employee_user_id "logical FK -> user.profile.id (employee's own userId)"
        string employee_name
        uuid client_id "logical FK -> business.client.id"
        string client_name
        string client_phone
        string client_email
        timestamp date_start
        timestamp date_end
        string note
        enum status
        string decline_reason
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_REQUEST_SERVICES {
        uuid id PK
        uuid appointment_request_id FK
        uuid service_id "logical FK -> business.service.id"
        string service_name
        uuid service_group_id "logical FK -> business.service_group.id"
        string price_currency
        long price_unscaled
        int price_scale
        long duration_minutes
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_DAY_OFFS {
        uuid id PK
        uuid business_id FK
        date start_date
        date end_date
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_SETTINGS {
        uuid id PK
        uuid business_id FK,UK
        int in_between_break_in_minutes
        string appointment_note
        bool automatic_approval
        bool automatic_completion "default true; gates the MarkAppointmentsCompleted job for this business"
        timestamp createdAt
        timestamp updatedAt
    }

    WORKING_HOURS {
        uuid id PK
        uuid business_id FK
        byte day_of_week
        time start_time
        time end_time
        timestamp createdAt
        timestamp updatedAt
    }

    APPOINTMENT_PERMISSION_GRANTS {
        uuid id PK
        uuid userId "logical FK -> user.profile.id; UK with business_id"
        uuid business_id FK
        bool can_view
        bool can_update
        bool can_delete
        bool suspended "mirrors business employee.suspended_at; set = 403 on every check"
        timestamp createdAt
        timestamp updatedAt
    }

    EXHAUSTED_EVENT {
        uuid id PK
        string original_topic
        string idempotency_key UK
        int attempt
        blob payload
        timestamp createdAt
        timestamp updatedAt
    }
```

`EXHAUSTED_EVENT` is not part of the appointments domain model — it's the shared DLT landing table (`core/data/eventstreaming/data`, one instance per service schema) that `KafkaEventConsumer`/`EmbeddedEventConsumer` write to when an event exhausts its retry budget, so a failed event survives a process restart or crash instead of being lost. See `AGENTS.md`'s event-streaming retry design notes.
