package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessCreateRequest
import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.operation.CreateBusiness
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.user.entity.UserLimits
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.UserLimitsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import library.schedule.DayOffRange
import library.schedule.Schedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CreateBusinessImplTest {

    private class SutFixture {
        val businessDataSource = mockk<BusinessDataSource> { coEvery { countBusinessesOwnedBy(any()) } returns 0 }
        val employeeDataSource = mockk<EmployeeDataSource>()
        val userLimitsDataSource = mockk<UserLimitsDataSource> {
            coEvery { lockForUser(any()) } answers { UserLimits.stub(userId = firstArg()) }
        }
        val userClient = mockk<UserClient>()
        val transactionManager = mockk<TransactionManager>()
        val sut = CreateBusinessImpl(
            businessDataSource,
            employeeDataSource,
            userLimitsDataSource,
            userClient,
            transactionManager,
        )
    }

    @Test
    fun `should refuse a new business when the owner already owns their maximum of businesses`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userLimitsDataSource.lockForUser(userId) } returns UserLimits.stub(userId, maxBusinesses = 2)
            coEvery { businessDataSource.countBusinessesOwnedBy(userId) } returns 2
        }

        whenn()
        val result = fixture.sut(
            userId,
            BusinessCreateRequest(name = "Name", currencyCode = "USD", timeZone = TimeZone.UTC),
        )

        then()
        assertTrue(result.exceptionOrNull() is CreateBusiness.Error.OwnedBusinessesLimitReached)
        coVerify(exactly = 0) { fixture.businessDataSource.createBusiness(any(), any(), any(), any()) }
    }

    @Test
    fun `should lock the owner's limits before creating a business one below their limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = Business.stub(name = "Name", currencyCode = "USD")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userLimitsDataSource.lockForUser(userId) } returns UserLimits.stub(userId, maxBusinesses = 2)
            coEvery { businessDataSource.countBusinessesOwnedBy(userId) } returns 1
            coEvery { userClient.getUserById(userId) } returns Result.success(UserSnapshot.stub(id = userId))
            coEvery { businessDataSource.createBusiness(userId, "Name", "USD", TimeZone.UTC) } returns business
            coEvery { employeeDataSource.createEmployee(any()) } returns Employee.stub(businessId = business.id)
        }

        whenn()
        val result = fixture.sut(
            userId,
            BusinessCreateRequest(name = "Name", currencyCode = "USD", timeZone = TimeZone.UTC),
        )

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.userLimitsDataSource.lockForUser(userId)
            fixture.businessDataSource.countBusinessesOwnedBy(userId)
            fixture.businessDataSource.createBusiness(userId, "Name", "USD", TimeZone.UTC)
        }
    }

    @Test
    fun `should create business successfully when valid data provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = Business.stub(name = "Name", currencyCode = "USD")
        val user = UserSnapshot.stub(
            id = userId,
            name = "Owner",
            lastName = "Ownerson",
            email = "owner@example.com",
            phone = "+10000000000",
        )
        val ownerEmployee = Employee.stub(businessId = business.id, userId = userId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.createBusiness(userId, "Name", "USD", TimeZone.UTC) } returns business
            coEvery { userClient.getUserById(userId) } returns Result.success(user)
            coEvery { employeeDataSource.createEmployee(any()) } returns ownerEmployee
        }

        whenn()
        val result = fixture.sut(
            userId,
            BusinessCreateRequest(name = "Name", currencyCode = "USD", timeZone = TimeZone.UTC),
        )

        then()
        assertTrue(result.isSuccess)
        assertEquals(
            business.copy(permissions = BusinessPermissions.FULL, employeeId = ownerEmployee.id, clientId = null),
            result.getOrNull(),
        )
        coVerify(exactly = 1) {
            fixture.employeeDataSource.createEmployee(
                match {
                    it.businessId == business.id &&
                        it.userId == userId &&
                        it.name == user.name &&
                        it.lastName == user.lastName &&
                        it.email == user.email &&
                        it.phone == user.phone &&
                        it.permissions == BusinessPermissions.FULL
                },
            )
        }
    }

    @Test
    fun `should give the owner the working week of the new business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = Business.stub(name = "Name", currencyCode = "USD").copy(
            schedule = Schedule().copy(dayOffs = listOf(DayOffRange(LocalDate(2026, 6, 22), LocalDate(2026, 6, 22)))),
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.createBusiness(userId, "Name", "USD", TimeZone.UTC) } returns business
            coEvery { userClient.getUserById(userId) } returns Result.success(UserSnapshot.stub(id = userId))
            coEvery { employeeDataSource.createEmployee(any()) } returns Employee.stub(businessId = business.id)
        }

        whenn()
        fixture.sut(userId, BusinessCreateRequest(name = "Name", currencyCode = "USD", timeZone = TimeZone.UTC))

        then()
        coVerify(exactly = 1) {
            fixture.employeeDataSource.createEmployee(match { it.schedule == business.schedule.workingWeek() })
        }
    }

    @Test
    fun `should return failure when name is too short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(
            Uuid.random(),
            BusinessCreateRequest(name = "A", currencyCode = "USD", timeZone = TimeZone.UTC),
        )

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateBusiness.Error.BusinessValidationError)
    }

    @Test
    fun `should return failure when name is too long`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(
            Uuid.random(),
            BusinessCreateRequest(name = "A".repeat(513), currencyCode = "USD", timeZone = TimeZone.UTC),
        )

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateBusiness.Error.BusinessValidationError)
    }

    @Test
    fun `should create another business when the user already owns one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val business = Business.stub(name = "Name", currencyCode = "USD")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.countBusinessesOwnedBy(userId) } returns 1
            coEvery { userClient.getUserById(userId) } returns Result.success(UserSnapshot.stub(id = userId))
            coEvery { businessDataSource.createBusiness(userId, "Name", "USD", TimeZone.UTC) } returns business
            coEvery { employeeDataSource.createEmployee(any()) } returns Employee.stub(businessId = business.id)
        }

        whenn()
        val result = fixture.sut(
            userId,
            BusinessCreateRequest(name = "Name", currencyCode = "USD", timeZone = TimeZone.UTC),
        )

        then()
        assertEquals(business.id, result.getOrNull()?.id)
    }

    @Test
    fun `should return failure when currency code is not a supported currency`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(
            userId,
            BusinessCreateRequest(name = "Name", currencyCode = "ABC", timeZone = TimeZone.UTC),
        )

        then()
        assertTrue(result.exceptionOrNull() is CreateBusiness.Error.InvalidCurrency)
        coVerify(exactly = 0) { fixture.businessDataSource.createBusiness(any(), any(), any(), any()) }
    }
}
