package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessLimits
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.CreateClient
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
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
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class CreateClientImplTest {

    private val requestUserId = Uuid.random()

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource> { coEvery { countClients(any()) } returns 0 }
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val businessDataSource = mockk<BusinessDataSource> {
            coEvery { lockBusiness(any(), RowLock.EXCLUSIVE) } returns LockedBusiness.stub("USD")
        }
        val transactionManager = mockk<TransactionManager>()
        val sut =
            CreateClientImpl(transactionManager, clientDataSource, businessPermissionDataSource, businessDataSource)

        init {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns
                ResourcePermission.FULL
        }

        fun grantPermission(permission: ResourcePermission) {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns
                permission
        }
    }

    @Test
    fun `should refuse a client when the business already has its maximum of clients`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns
                LockedBusiness.stub("USD", BusinessLimits.stub(maxClients = 7))
            coEvery { clientDataSource.countClients(businessId) } returns 7
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, Client.Detached.stub())

        then()
        assertTrue(result.exceptionOrNull() is CreateClient.Error.ClientsLimitReached)
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClient(any(), any()) }
    }

    @Test
    fun `should lock the business before checking permission and counting clients`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns
                LockedBusiness.stub("USD", BusinessLimits.stub(maxClients = 7))
            coEvery { clientDataSource.countClients(businessId) } returns 6
            coEvery { clientDataSource.createDetachedClient(businessId, any()) } returns client
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE)
            fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
            fixture.clientDataSource.countClients(businessId)
            fixture.clientDataSource.createDetachedClient(businessId, any())
        }
    }

    @Test
    fun `should return not found when creating a client for a business that does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) } returns null
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, Client.Detached.stub())

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should create detached client successfully when valid data provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, client) } returns client
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        val actual = result.getOrNull()
        val expected = client.toRemote()
        assertEquals(expected.id, actual?.id)
        assertEquals(expected.name, actual?.name)
        assertEquals(expected.lastName, actual?.lastName)
        assertEquals(expected.phone, actual?.phone)
        assertEquals(expected.email, actual?.email)
        assertEquals(expected.userId, actual?.userId)
    }

    @Test
    fun `should return the creation time of the created client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub()
        val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, client) } returns
                client.copy(createdAt = createdAt)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertEquals(createdAt, result.getOrThrow().createdAt)
    }

    @Test
    fun `should create detached client successfully when email is not provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(email = null)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, client) } returns client
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull()?.email)
    }

    @Test
    fun `should create detached client successfully when phone is not provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(phone = null)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, client) } returns client
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull()?.phone)
    }

    @Test
    fun `should return error when neither phone nor email is provided`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(phone = null, email = null)
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateClient.Error.MissingContactInfo)
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClient(any(), any()) }
    }

    @Test
    fun `should return error when the phone or email belongs to another client of the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, client) } throws
                Error.UniqueConstraintFailed("duplicate", IllegalStateException())
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.exceptionOrNull() is CreateClient.Error.ClientExist)
    }

    @Test
    fun `should return validation error when name is too long`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(name = "A".repeat(513))
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateClient.Error.ClientValidationError)
    }

    @Test
    fun `should return failure when user has read permission only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission(view = true, update = false, delete = false))
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClient(any(), any()) }
    }

    @Test
    fun `should return failure when user has no permission for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission.NONE)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClient(any(), any()) }
    }

    @Test
    fun `should assert permission against the requested business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, client) } returns client
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
        }
    }

    @Test
    fun `should return validation error when last name is too long`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(lastName = "A".repeat(513))
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateClient.Error.ClientValidationError)
    }

    @Test
    fun `should return validation error when phone contains letters`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(phone = "call-me-maybe")
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateClient.Error.ClientValidationError)
    }

    @Test
    fun `should return validation error when phone is too short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(phone = "12")
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateClient.Error.ClientValidationError)
    }

    @Test
    fun `should trim padded name, lastName, phone, email and description before persisting`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(
            name = "  Jane  ",
            lastName = "  Doe  ",
            phone = "  654321  ",
            email = "jane@doe.com",
            description = "  VIP client  ",
        )
        val trimmedClient = client.copy(
            name = "Jane",
            lastName = "Doe",
            phone = "654321",
            email = "jane@doe.com",
            description = "VIP client",
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, trimmedClient) } returns trimmedClient
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        assertEquals("Jane", result.getOrNull()?.name)
        assertEquals("Doe", result.getOrNull()?.lastName)
        assertEquals("654321", result.getOrNull()?.phone)
        assertEquals("jane@doe.com", result.getOrNull()?.email)
        assertEquals("VIP client", result.getOrNull()?.description)
        coVerify(exactly = 1) { fixture.clientDataSource.createDetachedClient(businessId, trimmedClient) }
    }

    @Test
    fun `should truncate an overly long description before persisting`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val tooLong = "A".repeat(Client.MAX_DESCRIPTION_LENGTH + 10)
        val truncated = tooLong.take(Client.MAX_DESCRIPTION_LENGTH)
        val client = Client.Detached.stub(description = tooLong)
        val trimmedClient = client.copy(description = truncated)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, trimmedClient) } returns trimmedClient
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        assertEquals(truncated, result.getOrNull()?.description)
    }

    @Test
    fun `should treat a blank phone as absent and not fail contact info check when email is present`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(phone = "   ")
        val trimmedClient = client.copy(phone = null)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.createDetachedClient(businessId, trimmedClient) } returns trimmedClient
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull()?.phone)
    }

    @Test
    fun `should return error when phone and email are blank`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(phone = "   ", email = "   ")
        fixture.transactionManager.mockTransaction()

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateClient.Error.MissingContactInfo)
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClient(any(), any()) }
    }

    @Test
    fun `should return validation error when email is malformed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val client = Client.Detached.stub(email = "not-an-email")
        with(fixture) {
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, client)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateClient.Error.ClientValidationError)
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClient(any(), any()) }
    }
}
