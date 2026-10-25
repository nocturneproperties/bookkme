package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.api.business.operation.UpdateBusiness
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.LockedBusiness
import com.bookk.business.domain.datasource.RowLock
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import library.permissions.ResourcePermission
import library.schedule.DayOfWeekSchedule
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UpdateBusinessImplTest {

    private val requestUserId = Uuid.random()

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>(relaxed = true)
        val serviceDataSource = mockk<ServiceDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = UpdateBusinessImpl(businessDataSource, serviceDataSource, businessPermissionDataSource, transactionManager, eventProducer)

        init {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.BUSINESS) } returns ResourcePermission.FULL
        }

        fun grantPermission(permission: ResourcePermission) {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.BUSINESS) } returns permission
        }
    }

    private fun updateModel(
        id: Uuid = Uuid.random(),
        name: String? = null,
        description: String? = null,
        address: String? = null,
        location: Business.Location? = null,
        currencyCode: String? = null,
        timeZone: TimeZone? = null,
        socials: List<Business.Social>? = null,
        schedule: Schedule? = null,
        dayOffs: List<DayOffRange> = emptyList()
    ) = BusinessUpdateModel(
        id = id,
        name = name,
        description = description,
        address = address,
        location = location,
        currencyCode = currencyCode,
        timeZone = timeZone,
        socials = socials,
        schedule = schedule?.copy(dayOffs = dayOffs)
    )

    private fun scheduleOf(vararg days: DayOfWeek) = Schedule(
        workingDays = days.toList(),
        workingHours = days.associateWith { listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))) }
    )

    @Test
    fun `should return success when truncated values are provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val businessId = Uuid.random()
        val updateModel = updateModel(
            id = businessId,
            name = "A".repeat(1000),
            description = "D".repeat(2000),
            address = "Addr".repeat(200),
            timeZone = TimeZone.UTC,
            socials = listOf(Business.Social(Business.SocialKind.INSTAGRAM, "V".repeat(500)))
        )

        whenn()
        val result = fixture.sut(requestUserId, updateModel)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.businessDataSource.updateBusiness(match {
                it.name?.length == Business.MAX_NAME_LENGTH &&
                it.description?.length == Business.MAX_DESCRIPTION_LENGTH &&
                it.address?.length == Business.MAX_ADDRESS_LENGTH &&
                it.socials?.firstOrNull()?.value?.length == Business.MAX_SOCIAL_LENGTH
            }, any())
        }
    }

    @Test
    fun `should return success when null values are provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val updateModel = updateModel(socials = emptyList())

        whenn()
        val result = fixture.sut(requestUserId, updateModel)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.businessDataSource.updateBusiness(match {
                it.name == null && it.description == null && it.address == null && it.socials?.isEmpty() == true
            }, any())
        }
    }

    @Test
    fun `should publish business updated event with the updated business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val businessId = Uuid.random()
        val schedule = scheduleOf(DayOfWeek.MONDAY)
        val updatedBusiness = Business.stub(
            id = businessId,
            name = "New Name",
            address = "New Address",
            schedule = schedule
        )
        coEvery { fixture.businessDataSource.updateBusiness(any(), any()) } returns updatedBusiness
        val updateModel = updateModel(
            id = businessId,
            name = "New Name",
            address = "New Address",
            timeZone = TimeZone.UTC,
            socials = emptyList()
        )

        whenn()
        val result = fixture.sut(requestUserId, updateModel)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<BusinessEvent.Updated> {
                it.business.id == updatedBusiness.id &&
                it.business.name == updatedBusiness.name &&
                it.business.address == updatedBusiness.address &&
                it.business.timeZone == updatedBusiness.timeZone
            }, any())
        }
    }

    @Test
    fun `should return failure when active day has no work hours`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY),
            workingHours = emptyMap()
        )

        whenn()
        val result = fixture.sut(requestUserId, updateModel(schedule = schedule))

        then()
        assertTrue(result.exceptionOrNull() is UpdateBusiness.Error.ActiveDayWithoutWorkHours)
    }

    @Test
    fun `should return failure when day off range start date is after end date`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 31), LocalDate(2099, 12, 30)))

        whenn()
        val result = fixture.sut(requestUserId, updateModel(schedule = scheduleOf(DayOfWeek.MONDAY), dayOffs = dayOffs))

        then()
        assertTrue(result.exceptionOrNull() is UpdateBusiness.Error.InvalidDayOffRange)
    }

    @Test
    fun `should return success when day off range covers a single day`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val singleDay = LocalDate(2099, 12, 30)
        val dayOffs = listOf(DayOffRange(singleDay, singleDay))

        whenn()
        val result = fixture.sut(requestUserId, updateModel(schedule = scheduleOf(DayOfWeek.MONDAY), dayOffs = dayOffs))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.businessDataSource.updateBusiness(match {
                it.schedule?.dayOffs == dayOffs
            }, any())
        }
    }

    @Test
    fun `should publish the updated schedule and day offs on the updated event`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val businessId = Uuid.random()
        val schedule = scheduleOf(DayOfWeek.SATURDAY)
        val dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
        val updatedBusiness = Business.stub(id = businessId, schedule = schedule.copy(dayOffs = dayOffs))
        coEvery { fixture.businessDataSource.updateBusiness(any(), any()) } returns updatedBusiness

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, schedule = schedule, dayOffs = dayOffs))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<BusinessEvent.Updated> {
                it.business.id == businessId &&
                it.business.schedule[DayOfWeek.SATURDAY] == DayOfWeekSchedule(
                    workingTime = listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0))),
                    isActive = true
                ) &&
                !it.business.schedule[DayOfWeek.MONDAY].isActive &&
                it.business.schedule.dayOffs == listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
            }, any())
        }
    }

    @Test
    fun `should publish the stored schedule when the update does not change it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val businessId = Uuid.random()
        val schedule = scheduleOf(DayOfWeek.MONDAY)
        coEvery { fixture.businessDataSource.updateBusiness(any(), any()) } returns
            Business.stub(id = businessId, schedule = schedule)

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, name = "New Name"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(match<BusinessEvent.Updated> {
                it.business.schedule[DayOfWeek.MONDAY].isActive
            }, any())
        }
    }

    @Test
    fun `should publish the event with the timestamp persisted on the business row`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val businessId = Uuid.random()
        val persistedAt = slot<Instant>()
        coEvery {
            fixture.businessDataSource.updateBusiness(any(), capture(persistedAt))
        } returns Business.stub(id = businessId)

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, name = "New Name"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<BusinessEvent.Updated> { it.updatedAt == persistedAt.captured },
                any()
            )
        }
    }

    @Test
    fun `should publish exactly one event per update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val businessId = Uuid.random()
        val schedule = scheduleOf(DayOfWeek.MONDAY)
        coEvery { fixture.businessDataSource.updateBusiness(any(), any()) } returns
            Business.stub(id = businessId, schedule = schedule)

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, schedule = schedule))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.eventProducer.send(any(BusinessEvent.Updated::class), any()) }
    }

    @Test
    fun `should return failure when user has no update permission for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        fixture.grantPermission(ResourcePermission(view = true, update = false, delete = false))

        whenn()
        val result = fixture.sut(requestUserId, updateModel(name = "New Name"))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should return failure when user has no permission record for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        fixture.grantPermission(ResourcePermission.NONE)

        whenn()
        val result = fixture.sut(requestUserId, updateModel(name = "New Name"))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should not update business or publish event when user has no update permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        fixture.grantPermission(ResourcePermission(view = true, update = false, delete = false))

        whenn()
        val result = fixture.sut(requestUserId, updateModel(name = "New Name", schedule = scheduleOf(DayOfWeek.MONDAY)))

        then()
        assertEquals(false, result.isSuccess)
        coVerify(exactly = 0) { fixture.businessDataSource.updateBusiness(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.Updated::class), any()) }
    }

    @Test
    fun `should assert update permission against the business from the update model`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val businessId = Uuid.random()

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, name = "New Name"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.BUSINESS) }
    }

    @Test
    fun `should not update business when schedule is invalid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val schedule = Schedule(workingDays = listOf(DayOfWeek.MONDAY), workingHours = emptyMap())

        whenn()
        val result = fixture.sut(requestUserId, updateModel(schedule = schedule))

        then()
        assertEquals(false, result.isSuccess)
        coVerify(exactly = 0) { fixture.businessDataSource.updateBusiness(any(), any()) }
    }

    @Test
    fun `should change the currency when the business has no services`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.hasServices(businessId) } returns false
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "EUR"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessDataSource.updateBusiness(match { it.currencyCode == "EUR" }, any()) }
    }

    @Test
    fun `should return failure when changing the currency of a business that has services`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.hasServices(businessId) } returns true
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "EUR"))

        then()
        assertTrue(result.exceptionOrNull() is UpdateBusiness.Error.CurrencyLocked)
        coVerify(exactly = 0) { fixture.businessDataSource.updateBusiness(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.Updated::class), any()) }
    }

    @Test
    fun `should accept the stored currency unchanged when the business has services`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.hasServices(businessId) } returns true
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, name = "New Name", currencyCode = "USD"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessDataSource.updateBusiness(match { it.currencyCode == "USD" }, any()) }
    }

    @Test
    fun `should return failure when currency code is not a supported currency`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.hasServices(businessId) } returns false
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "USDD"))

        then()
        assertTrue(result.exceptionOrNull() is UpdateBusiness.Error.InvalidCurrency)
        coVerify(exactly = 0) { fixture.businessDataSource.updateBusiness(any(), any()) }
    }

    @Test
    fun `should return not found when changing the currency of an unknown business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns null
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "EUR"))

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.businessPermissionDataSource.getPermission(any(), any(), any()) }
        coVerify(exactly = 0) { fixture.businessDataSource.updateBusiness(any(), any()) }
    }

    @Test
    fun `should not lock the business when the update leaves the currency untouched`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(requestUserId, updateModel(name = "New Name"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.businessDataSource.lockBusiness(any(), any()) }
    }

    @Test
    fun `should not check services or update when user has no update permission to change the currency`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission(view = true, update = false, delete = false))
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "EUR"))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.serviceDataSource.hasServices(any()) }
        coVerify(exactly = 0) { fixture.businessDataSource.updateBusiness(any(), any()) }
    }

    @Test
    fun `should lock the business before any other read when the currency is sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { serviceDataSource.hasServices(businessId) } returns false
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "EUR"))

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE)
            fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.BUSINESS)
            fixture.serviceDataSource.hasServices(businessId)
            fixture.businessDataSource.updateBusiness(any(), any())
        }
    }

    @Test
    fun `should allow replacing an unsupported stored currency even when the business has services`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("usd")
            coEvery { serviceDataSource.hasServices(businessId) } returns true
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "USD"))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessDataSource.updateBusiness(match { it.currencyCode == "USD" }, any()) }
    }

    @Test
    fun `should return failure when replacing an unsupported stored currency with another unsupported one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("usd")
        }

        whenn()
        val result = fixture.sut(requestUserId, updateModel(id = businessId, currencyCode = "XXX"))

        then()
        assertTrue(result.exceptionOrNull() is UpdateBusiness.Error.InvalidCurrency)
        coVerify(exactly = 0) { fixture.businessDataSource.updateBusiness(any(), any()) }
    }
}
