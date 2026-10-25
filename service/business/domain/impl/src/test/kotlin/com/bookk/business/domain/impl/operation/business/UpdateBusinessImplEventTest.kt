package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventProducer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaTestBroker
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.decodeFromByteArray
import library.permissions.ResourcePermission
import library.schedule.DayOffRange
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UpdateBusinessImplEventTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
            KafkaTestBroker.createTopic(BusinessEvent.Updated.TOPIC, partitions = 3)
        }
    }

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>(relaxed = true)
        val serviceDataSource = mockk<ServiceDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = KafkaEventProducer(
            servers = KafkaTestBroker.servers,
            client = "update-business-test",
            protoBuf = KafkaTestBroker.protoBuf
        )
        val sut = UpdateBusinessImpl(businessDataSource, serviceDataSource, businessPermissionDataSource, transactionManager, eventProducer)

        init {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.BUSINESS) } returns ResourcePermission.FULL
        }
    }

    private val requestUserId = Uuid.random()

    private fun updateModel(id: Uuid, schedule: Schedule) = BusinessUpdateModel(
        id = id, name = "Salon", description = null, address = null, location = null,
        currencyCode = null, timeZone = null, socials = null, schedule = schedule
    )

    private fun publishedFor(businessId: Uuid) = KafkaTestBroker
        .awaitRecords(BusinessEvent.Updated.TOPIC, expected = 1) { it.key() == businessId.toString() }
        .map { it.key() to KafkaTestBroker.protoBuf.decodeFromByteArray<BusinessEvent.Updated>(it.value()) }

    @Test
    fun `should publish a schedule the appointments service can decode`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.SATURDAY),
            workingHours = mapOf(DayOfWeek.SATURDAY to listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0)))),
            dayOffs = listOf(DayOffRange(LocalDate(2099, 12, 30), LocalDate(2099, 12, 31)))
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.updateBusiness(any(), any()) } returns
                Business.stub(id = businessId, schedule = schedule)
        }

        whenn()
        val published = withContext(Dispatchers.IO) {
            fixture.sut(requestUserId, updateModel(businessId, schedule))
            publishedFor(businessId)
        }

        then()
        val (key, event) = published.single()
        assertEquals(businessId.toString(), key)
        assertEquals(schedule, event.business.schedule)
        assertEquals(
            listOf(WorkHour(LocalTime(10, 0), LocalTime(14, 0))),
            event.business.schedule[DayOfWeek.SATURDAY].workingTime
        )
    }

    @Test
    fun `should publish the persisted version as the event timestamp`() = runIntegrationTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val persisted = mutableListOf<kotlin.time.Instant>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.updateBusiness(any(), any()) } answers {
                persisted += secondArg<kotlin.time.Instant>()
                Business.stub(id = businessId)
            }
        }

        whenn()
        val published = withContext(Dispatchers.IO) {
            fixture.sut(requestUserId, updateModel(businessId, Schedule()))
            publishedFor(businessId)
        }

        then()
        assertEquals(persisted.single(), published.single().second.updatedAt)
    }
}
