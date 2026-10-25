package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.BusinessWorkingHoursTable
import com.bookk.business.data.orm.table.ServiceGroupTable
import com.bookk.business.data.orm.table.ServiceTable
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ServiceDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(
            BusinessTable, BusinessDashboardTable, BusinessPermissionGrantsTable, BusinessWorkingHoursTable, BusinessDayOffTable,
            ServiceGroupTable, ServiceTable
        )
        val sut = ServiceDataSourceImpl()
        val businessSut = BusinessDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            val userId = Uuid.random()
            businessId = suspendTransaction {
                businessSut.createBusiness(userId, "Test Business", "USD", TimeZone.UTC)
            }.id
        }
    }

    @Test
    fun `should create service group and retrieve`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = ServiceGroup.stub(businessId = fixture.businessId)

        whenn()
        val created = suspendTransaction { fixture.sut.createServiceGroup(group) }
        val groups = suspendTransaction { fixture.sut.getServiceGroups(fixture.businessId) }

        then()
        assertEquals(1, groups.size)
        assertEquals(group.name, groups.first().name)
        assertEquals(fixture.businessId, created.businessId)
    }

    @Test
    fun `should return empty list when no groups exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val groups = suspendTransaction { fixture.sut.getServiceGroups(fixture.businessId) }

        then()
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `should create service and retrieve by business id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }
        val service = Service.stub(businessId = fixture.businessId, group = group)

        whenn()
        val created = suspendTransaction { fixture.sut.createService(service) }
        val services = suspendTransaction { fixture.sut.getServices(fixture.businessId) }

        then()
        assertEquals(1, services.size)
        assertEquals(service.name, services.first().name)
        assertEquals(fixture.businessId, created.businessId)
    }

    @Test
    fun `should return empty list when no services exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val services = suspendTransaction { fixture.sut.getServices(fixture.businessId) }

        then()
        assertTrue(services.isEmpty())
    }

    @Test
    fun `should retrieve services by ids`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }
        val created = suspendTransaction {
            fixture.sut.createService(Service.stub(businessId = fixture.businessId, group = group))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getServicesByIds(listOf(created.id)) }

        then()
        assertEquals(1, found.size)
        assertEquals(created.id, found.first().id)
    }

    @Test
    fun `should edit service and persist changes`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }
        val created = suspendTransaction {
            fixture.sut.createService(Service.stub(businessId = fixture.businessId, group = group, isAvailable = true))
        }

        whenn()
        suspendTransaction { fixture.sut.editService(created.copy(isAvailable = false)) }
        val services = suspendTransaction { fixture.sut.getServices(fixture.businessId) }

        then()
        assertEquals(false, services.first().isAvailable)
    }

    @Test
    fun `should delete service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }
        val created = suspendTransaction {
            fixture.sut.createService(Service.stub(businessId = fixture.businessId, group = group))
        }

        whenn()
        suspendTransaction { fixture.sut.deleteService(created.id, fixture.businessId) }
        val services = suspendTransaction { fixture.sut.getServices(fixture.businessId) }

        then()
        assertTrue(services.isEmpty())
    }

    @Test
    fun `should delete service group`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }

        whenn()
        suspendTransaction { fixture.sut.deleteServiceGroup(group.id, fixture.businessId) }
        val groups = suspendTransaction { fixture.sut.getServiceGroups(fixture.businessId) }

        then()
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `should report that a business has services once one is created`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }
        suspendTransaction { fixture.sut.createService(Service.stub(businessId = fixture.businessId, group = group)) }

        whenn()
        val hasServices = suspendTransaction { fixture.sut.hasServices(fixture.businessId) }

        then()
        assertTrue(hasServices)
    }

    @Test
    fun `should report that a business without services has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        suspendTransaction { fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId)) }

        whenn()
        val hasServices = suspendTransaction { fixture.sut.hasServices(fixture.businessId) }

        then()
        assertFalse(hasServices)
    }

    @Test
    fun `should not count services of another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val otherGroup = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = otherBusinessId))
        }
        suspendTransaction { fixture.sut.createService(Service.stub(businessId = otherBusinessId, group = otherGroup)) }

        whenn()
        val hasServices = suspendTransaction { fixture.sut.hasServices(fixture.businessId) }

        then()
        assertFalse(hasServices)
    }

    @Test
    fun `should persist a renamed service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }
        val created = suspendTransaction {
            fixture.sut.createService(Service.stub(businessId = fixture.businessId, group = group, name = "Cut"))
        }

        whenn()
        suspendTransaction { fixture.sut.editService(created.copy(name = "Haircut")) }
        val services = suspendTransaction { fixture.sut.getServices(fixture.businessId) }

        then()
        assertEquals("Haircut", services.single().name)
    }

    @Test
    fun `should not edit a service that belongs to another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val otherGroup = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = otherBusinessId))
        }
        val otherService = suspendTransaction {
            fixture.sut.createService(Service.stub(businessId = otherBusinessId, group = otherGroup, name = "Theirs"))
        }

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.editService(otherService.copy(businessId = fixture.businessId, name = "Taken over"))
            }
        }
        val otherServices = suspendTransaction { fixture.sut.getServices(otherBusinessId) }
        val ownServices = suspendTransaction { fixture.sut.getServices(fixture.businessId) }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        assertEquals("Theirs", otherServices.single().name)
        assertTrue(ownServices.isEmpty())
    }

    @Test
    fun `should report not found when editing an unknown service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId))
        }

        whenn()
        val result = runCatching {
            suspendTransaction { fixture.sut.editService(Service.stub(businessId = fixture.businessId, group = group)) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should not delete a service group that belongs to another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val otherGroup = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = otherBusinessId))
        }
        suspendTransaction { fixture.sut.createService(Service.stub(businessId = otherBusinessId, group = otherGroup)) }

        whenn()
        suspendTransaction { fixture.sut.deleteServiceGroup(otherGroup.id, fixture.businessId) }
        val otherGroups = suspendTransaction { fixture.sut.getServiceGroups(otherBusinessId) }
        val otherServices = suspendTransaction { fixture.sut.getServices(otherBusinessId) }

        then()
        assertEquals(listOf(otherGroup.id), otherGroups.map { it.id })
        assertEquals(1, otherServices.size)
    }

    @Test
    fun `should return the service group of the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val group = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = fixture.businessId, name = "Hair"))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getServiceGroup(group.id, fixture.businessId) }

        then()
        assertEquals(group.id, found?.id)
        assertEquals("Hair", found?.name)
        assertEquals(fixture.businessId, found?.businessId)
    }

    @Test
    fun `should not return a service group of another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val otherGroup = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = otherBusinessId))
        }

        whenn()
        val found = suspendTransaction { fixture.sut.getServiceGroup(otherGroup.id, fixture.businessId) }

        then()
        assertNull(found)
    }

    @Test
    fun `should not delete a service that belongs to another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val otherBusinessId = suspendTransaction {
            fixture.businessSut.createBusiness(Uuid.random(), "Other Business", "USD", TimeZone.UTC)
        }.id
        val otherGroup = suspendTransaction {
            fixture.sut.createServiceGroup(ServiceGroup.stub(businessId = otherBusinessId))
        }
        val otherService = suspendTransaction {
            fixture.sut.createService(Service.stub(businessId = otherBusinessId, group = otherGroup))
        }

        whenn()
        suspendTransaction { fixture.sut.deleteService(otherService.id, fixture.businessId) }
        val otherServices = suspendTransaction { fixture.sut.getServices(otherBusinessId) }

        then()
        assertEquals(listOf(otherService.id), otherServices.map { it.id })
    }
}
