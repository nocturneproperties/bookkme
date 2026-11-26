# React to a seeded demo business

`BusinessEvent.DemoBusinessSeeded` → `SeedDemoAppointments`

Produced by [React to a new demo account](../business/on-demo-account-created.md).
Appointments turns the sample business on and fills about a week of bookings
around now, so the demo shows a working calendar. The event carries only ids;
the business, its owner employee, the sample clients and services are read
from business, outside the transaction, the way staff bookings read them
(`getBusinessById`, one `getStaffBookingContext` per sample client).

Every sample appointment goes through the same checks as a real booking: the
business and employee schedules (`AppointmentSettings.requireBookable`) and the
employee's other bookings (`EmployeeBookings.requireFree`), after the settings
row is locked. A slot that fails is left out instead of failing the seed, so
weekends and days off simply have no samples. Only the check that a booking
lies in the future is skipped, since half the samples are in the past.

```mermaid
flowchart TD
    Start([BusinessEvent.DemoBusinessSeeded businessId ownerUserId employeeId serviceIds clientIds]) --> Empty{serviceIds or clientIds empty?}
    Empty -- Yes --> Done0([Done])
    Empty -- No --> Business[BusinessClient.getBusinessById businessId]
    Business -- 404 --> Done1([Done - business already gone])
    Business -- ok --> Contexts[BusinessClient.getStaffBookingContext per sample client]
    Contexts -- failure --> Retry([Failure - retried through the DLT])
    Contexts -- ok --> Tx[[Begin transaction]]
    Tx --> Deleted{DeletedUserDataSource.isDeletedForShare ownerUserId?}
    Deleted -- Yes --> Done2([Commit - nothing seeded])
    Deleted -- No --> Exists{AppointmentSettingsDataSource.get businessId exists?}
    Exists -- Yes --> Done3([Commit - already seeded or enabled by the owner])
    Exists -- No --> Enable[attachBusiness snapshot, setPermission owner FULL, settingsDataSource.create - as Enable appointments]
    Enable --> Lock[AppointmentSettingsDataSource.getForUpdate businessId]
    Lock --> Plan[DemoBookingPlan - 10:00, 13:00, 15:30 on each day from 3 days ago to 3 days ahead, business time zone]
    Plan --> Check{requireBookable and requireFree?}
    Check -- No --> Plan
    Check -- Yes --> Create[AppointmentDataSource.create SCHEDULED - client and service rotate through the samples]
    Create --> Visit[eventProducer.send AppointmentEvent.ClientVisitBooked businessId clientId end]
    Visit --> Plan
    Plan -- done --> Requests[2 PENDING requests at 11:30 on the next bookable days - initiator and client account = owner]
    Requests --> Commit([Commit])
```

The pending requests name a sample client but carry the owner's account as
the requesting user (`RequestClientSnapshot.userId` can't be empty), so the
owner can try approving and declining them. Nothing is announced to
notifications: the owner gets no push or email for the samples.

**Consumed by:** `AppointmentEvent.ClientVisitBooked` → [business: record
the client visit](../business/on-client-visit-booked.md).
