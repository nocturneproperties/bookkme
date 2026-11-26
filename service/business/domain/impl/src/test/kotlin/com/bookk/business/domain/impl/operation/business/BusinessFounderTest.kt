package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.StoredBusinessLink
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.api.UserSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BusinessFounderTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val businessLinkDataSource = mockk<BusinessLinkDataSource>(relaxUnitFun = true)
        val sut = BusinessFounder(businessDataSource, employeeDataSource, businessLinkDataSource)
        val owner = UserSnapshot.stub(name = "Ann", lastName = "Lee")
        val business = Business.stub(ownerId = owner.id, demo = true)
        val employee = slot<Employee>()
        val plan = BusinessPlan("Sample Studio", "EUR", TimeZone.UTC, demo = true)

        init {
            coEvery { businessDataSource.createBusiness(owner.id, "Sample Studio", "EUR", TimeZone.UTC, true) } returns
                business
            coEvery { employeeDataSource.createEmployee(capture(employee)) } answers { firstArg() }
        }
    }

    @Test
    fun `should make the owner the business's first employee with every permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessLinkDataSource.getLinkByNameForUpdate(any()) } returns null

        whenn()
        val founded = fixture.sut.found(fixture.owner, fixture.plan)

        then()
        assertEquals(fixture.owner.id, fixture.employee.captured.userId)
        assertEquals("Ann", fixture.employee.captured.name)
        assertEquals(BusinessPermissions.FULL, fixture.employee.captured.permissions)
        assertEquals(fixture.employee.captured.id, founded.employeeId)
        assertEquals(BusinessPermissions.FULL, founded.permissions)
    }

    @Test
    fun `should pick another demo name when the first one is taken`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessLinkDataSource.getLinkByNameForUpdate(any()) } returnsMany listOf(
            StoredBusinessLink.stub(name = "demo-taken00"),
            null,
        )

        whenn()
        fixture.sut.found(fixture.owner, fixture.plan)

        then()
        coVerify(exactly = 2) { fixture.businessLinkDataSource.getLinkByNameForUpdate(any()) }
        coVerify(exactly = 1) {
            fixture.businessLinkDataSource.createLink(fixture.business.id, match { BusinessLinkPolicy.isDemoName(it) })
        }
    }

    @Test
    fun `should fail instead of sharing a link when every demo name it tries is taken`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessLinkDataSource.getLinkByNameForUpdate(any()) } returns StoredBusinessLink.stub()

        whenn()
        val result = runSuspendCatching { fixture.sut.found(fixture.owner, fixture.plan) }

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException) { result.toString() }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }
}
