package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.api.employee.operation.CreateEmployeeInvitation
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.business.domain.datasource.LockedBusiness
import com.bookk.business.domain.datasource.RowLock
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class CreateEmployeeInvitationImplTest {

    private class SutFixture {
        val invitationDataSource = mockk<EmployeeInvitationDataSource>()
        val businessDataSource = mockk<BusinessDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = CreateEmployeeInvitationImpl(invitationDataSource, businessDataSource, businessPermissionDataSource, transactionManager)
    }

    @Test
    fun `should create invitation successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        val persisted = slot<EmployeeInvitation>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns 0L
            coEvery { invitationDataSource.createInvitation(capture(persisted)) } answers { persisted.captured }
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(requestUserId, persisted.captured.invitedBy)
        assertEquals(businessId, persisted.captured.businessId)
        assertEquals(EmployeeInvitationStatus.PENDING, persisted.captured.status)
        assertNotNull(persisted.captured.code)
        coVerify(exactly = 1) { fixture.invitationDataSource.createInvitation(any()) }
    }

    @Test
    fun `should send a hashed code to the datasource and return the plaintext code to the caller`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        val persisted = slot<EmployeeInvitation>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns 0L
            coEvery { invitationDataSource.createInvitation(capture(persisted)) } answers { persisted.captured }
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isSuccess)
        val plainCode = requireNotNull(result.getOrNull()?.code)
        val hashSentToDatasource = requireNotNull(persisted.captured.code)
        assertNotEquals(plainCode, hashSentToDatasource)
        assertEquals(EmployeeInvitationCode.hash(plainCode), hashSentToDatasource)
    }

    @Test
    fun `should retry with a new code when the generated code collides`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        val invitations = mutableListOf<EmployeeInvitation>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns 0L
            coEvery { invitationDataSource.createInvitation(any()) } answers {
                val invitation = firstArg<EmployeeInvitation>()
                invitations.add(invitation)
                if (invitations.size == 1) throw Error.UniqueConstraintFailed("", RuntimeException())
                invitation
            }
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(2, invitations.size)
        assertTrue(invitations[0].code != invitations[1].code)
    }

    @Test
    fun `should return failure when code generation keeps colliding`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns 0L
            coEvery {
                invitationDataSource.createInvitation(any())
            } throws Error.UniqueConstraintFailed("", RuntimeException())
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should return failure when permission denied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = true, update = false, delete = false)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.invitationDataSource.countPendingInvitations(any()) }
        coVerify(exactly = 0) { fixture.invitationDataSource.createInvitation(any()) }
    }

    @Test
    fun `should return failure when business does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns null
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.businessPermissionDataSource.getPermission(any(), any(), any()) }
    }

    @Test
    fun `should return failure when the business already has the maximum number of pending invitations`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns CreateEmployeeInvitation.MAX_PENDING_INVITATIONS.toLong()
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is CreateEmployeeInvitation.Error.PendingInvitationsLimitReached)
        coVerify(exactly = 0) { fixture.invitationDataSource.createInvitation(any()) }
    }

    @Test
    fun `should create invitation when one slot below the pending invitations limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns CreateEmployeeInvitation.MAX_PENDING_INVITATIONS.toLong() - 1
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns 0L
            coEvery { invitationDataSource.createInvitation(any()) } answers { firstArg() }
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.invitationDataSource.createInvitation(any()) }
    }

    @Test
    fun `should lock the business before any other read so the counts see every committed invitation`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns 0L
            coEvery { invitationDataSource.createInvitation(any()) } answers { firstArg() }
        }

        whenn()
        fixture.sut(requestUserId, businessId)

        then()
        coVerifyOrder {
            fixture.businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE)
            fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES)
            fixture.invitationDataSource.countPendingInvitations(businessId)
            fixture.invitationDataSource.countInvitationsCreatedSince(businessId, any())
            fixture.invitationDataSource.createInvitation(any())
        }
    }
    @Test
    fun `should return failure when the business already created the maximum number of invitations today`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns CreateEmployeeInvitation.MAX_INVITATIONS_PER_DAY.toLong()
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is CreateEmployeeInvitation.Error.DailyInvitationsLimitReached)
        coVerify(exactly = 0) { fixture.invitationDataSource.createInvitation(any()) }
    }

    @Test
    fun `should create invitation when one below the daily invitations limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, any()) } returns CreateEmployeeInvitation.MAX_INVITATIONS_PER_DAY.toLong() - 1
            coEvery { invitationDataSource.createInvitation(any()) } answers { firstArg() }
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should count todays invitations over the last 24 hours`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestUserId = Uuid.random()
        val businessId = Uuid.random()
        val since = slot<Instant>()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.EMPLOYEES) } returns ResourcePermission(view = false, update = true, delete = false)
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns LockedBusiness("USD")
            coEvery { invitationDataSource.countPendingInvitations(businessId) } returns 0L
            coEvery { invitationDataSource.countInvitationsCreatedSince(businessId, capture(since)) } returns 0L
            coEvery { invitationDataSource.createInvitation(any()) } answers { firstArg() }
        }

        whenn()
        val before = Clock.System.now()
        fixture.sut(requestUserId, businessId)
        val after = Clock.System.now()

        then()
        assertTrue(since.captured in (before - 24.hours)..(after - 24.hours))
    }
}
