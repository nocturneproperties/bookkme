package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentPermissionGrantsTable
import com.bookk.appointments.data.orm.table.DayOffsTable
import com.bookk.appointments.data.orm.table.WorkingHoursTable
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AppointmentPermissionDataSourceImplTest {

    private class SutFixture {
        val db =
            createTestDatabase(
                AppointmentBusinessTable,
                WorkingHoursTable,
                DayOffsTable,
                AppointmentPermissionGrantsTable,
            )
        val sut = AppointmentPermissionDataSourceImpl()
        val subscriptionSut = AppointmentSubscriptionDataSourceImpl()
        lateinit var businessId: Uuid

        suspend fun setup() {
            val snapshot = BusinessSnapshot.stub()
            suspendTransaction { subscriptionSut.attachBusiness(snapshot) }
            businessId = snapshot.id
        }
    }

    @Test
    fun `should return none permission when not set`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val permission = suspendTransaction { fixture.sut.getPermission(Uuid.random(), fixture.businessId) }

        then()
        assertEquals(ResourcePermission.NONE, permission)
    }

    @Test
    fun `should create a permission row when setting permissions for the first time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        val permission = ResourcePermission(view = true, update = true, delete = false)

        whenn()
        suspendTransaction { fixture.sut.setPermission(userId, fixture.businessId, permission, suspended = false) }
        val stored = suspendTransaction { fixture.sut.getPermission(userId, fixture.businessId) }

        then()
        assertEquals(permission, stored)
    }

    @Test
    fun `should overwrite an existing permission when set again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction {
            fixture.sut.setPermission(
                userId,
                fixture.businessId,
                ResourcePermission(view = true, update = false, delete = false),
                suspended = false,
            )
        }

        whenn()
        suspendTransaction {
            fixture.sut.setPermission(userId, fixture.businessId, ResourcePermission.FULL, suspended = false)
        }
        val stored = suspendTransaction { fixture.sut.getPermission(userId, fixture.businessId) }

        then()
        assertEquals(ResourcePermission.FULL, stored)
    }

    @Test
    fun `should delete permissions for a user across businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction {
            fixture.sut.setPermission(userId, fixture.businessId, ResourcePermission.FULL, suspended = false)
        }

        whenn()
        suspendTransaction { fixture.sut.deleteForUser(userId) }
        val permission = suspendTransaction { fixture.sut.getPermission(userId, fixture.businessId) }

        then()
        assertEquals(ResourcePermission.NONE, permission)
    }

    @Test
    fun `should not fail deleting permissions for a user with none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.deleteForUser(Uuid.random()) } }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should reject a permission check while the employee is suspended`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction {
            fixture.sut.setPermission(userId, fixture.businessId, ResourcePermission.NONE, suspended = true)
        }

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.getPermission(userId, fixture.businessId) } }

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
    }

    @Test
    fun `should grant the stored permission again once the employee is reinstated`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val userId = Uuid.random()
        suspendTransaction {
            fixture.sut.setPermission(userId, fixture.businessId, ResourcePermission.NONE, suspended = true)
        }

        whenn()
        suspendTransaction {
            fixture.sut.setPermission(userId, fixture.businessId, ResourcePermission.FULL, suspended = false)
        }
        val stored = suspendTransaction { fixture.sut.getPermission(userId, fixture.businessId) }

        then()
        assertEquals(ResourcePermission.FULL, stored)
    }

    @Test
    fun `should count a user with any grant as a member, a suspended one included`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val employee = Uuid.random()
        val suspended = Uuid.random()
        suspendTransaction {
            fixture.sut.setPermission(employee, fixture.businessId, ResourcePermission.NONE, suspended = false)
            fixture.sut.setPermission(suspended, fixture.businessId, ResourcePermission.FULL, suspended = true)
        }

        whenn()
        val members = suspendTransaction {
            listOf(employee, suspended).map { fixture.sut.isMember(it, fixture.businessId) }
        }

        then()
        assertEquals(listOf(true, true), members)
    }

    @Test
    fun `should not count a user without a grant at the business as a member`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.setup()
        val elsewhere = Uuid.random()
        val otherBusiness = BusinessSnapshot.stub()
        suspendTransaction {
            fixture.subscriptionSut.attachBusiness(otherBusiness)
            fixture.sut.setPermission(elsewhere, otherBusiness.id, ResourcePermission.FULL, suspended = false)
        }

        whenn()
        val member = suspendTransaction { fixture.sut.isMember(elsewhere, fixture.businessId) }

        then()
        assertEquals(false, member)
    }
}
