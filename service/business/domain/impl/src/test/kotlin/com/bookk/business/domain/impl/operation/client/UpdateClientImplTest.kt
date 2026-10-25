package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.business.domain.api.client.operation.UpdateClient
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UpdateClientImplTest {

    private val requestUserId = Uuid.random()
    private val businessId = Uuid.random()
    private val clientId = Uuid.random()

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = UpdateClientImpl(transactionManager, clientDataSource, businessPermissionDataSource)

        init {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns ResourcePermission.FULL
        }

        fun grantPermission(permission: ResourcePermission) {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns permission
        }
    }

    private fun updateModel(
        id: Uuid = clientId,
        name: String? = null,
        lastName: String? = null,
        phone: String? = null,
        email: String? = null,
        description: String? = null
    ) = ClientUpdateModel(id = id, name = name, lastName = lastName, phone = phone, email = email, description = description)

    @Test
    fun `should update a detached client's personal info and description`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        val requested = updateModel(name = "Jane", lastName = "Roe", phone = "654321", email = "jane@roe.com", description = "VIP client")
        val updated = existing.copy(name = "Jane", lastName = "Roe", phone = "654321", email = "jane@roe.com", description = "VIP client")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
            coEvery { clientDataSource.getClient(businessId, requested.phone!!) } returns null
            coEvery { clientDataSource.updateClient(businessId, requested) } returns updated
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.isSuccess)
        assertEquals("Jane", result.getOrNull()?.name)
        assertEquals("VIP client", result.getOrNull()?.description)
    }

    @Test
    fun `should trim and truncate the description before persisting it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        val tooLong = "A".repeat(Client.MAX_DESCRIPTION_LENGTH + 10)
        val truncated = tooLong.take(Client.MAX_DESCRIPTION_LENGTH)
        val requested = updateModel(description = "  $tooLong  ")
        val trimmedModel = requested.copy(description = truncated)
        val updated = existing.copy(description = truncated)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
            coEvery { clientDataSource.updateClient(businessId, trimmedModel) } returns updated
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.clientDataSource.updateClient(businessId, trimmedModel) }
    }

    @Test
    fun `should allow updating only the description of an integrated client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Integrated.stub(id = clientId)
        val requested = updateModel(description = "Prefers online booking")
        val updated = existing.copy(description = "Prefers online booking")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
            coEvery { clientDataSource.updateClient(businessId, requested) } returns updated
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.isSuccess)
        assertEquals("Prefers online booking", result.getOrNull()?.description)
    }

    @Test
    fun `should return failure when trying to change the name of an integrated client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Integrated.stub(id = clientId)
        val requested = updateModel(name = "Jane")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.PersonalInfoNotEditable)
        coVerify(exactly = 0) { fixture.clientDataSource.updateClient(any(), any()) }
    }

    @Test
    fun `should return failure when trying to change the phone of an integrated client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Integrated.stub(id = clientId)
        val requested = updateModel(phone = "999999")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.PersonalInfoNotEditable)
        coVerify(exactly = 0) { fixture.clientDataSource.updateClient(any(), any()) }
    }

    @Test
    fun `should return not found when client does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns null
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, updateModel(description = "notes"))

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.NotFound)
    }

    @Test
    fun `should return not found when the client disappears between the lookup and the update`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        val requested = updateModel(description = "notes")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
            coEvery { clientDataSource.updateClient(businessId, requested) } returns null
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.NotFound)
    }

    @Test
    fun `should return validation error when updated name is too long`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, updateModel(name = "A".repeat(513)))

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.ClientValidationError)
        coVerify(exactly = 0) { fixture.clientDataSource.updateClient(any(), any()) }
    }

    @Test
    fun `should return validation error when updated phone is invalid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, updateModel(phone = "call-me-maybe"))

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.ClientValidationError)
        coVerify(exactly = 0) { fixture.clientDataSource.updateClient(any(), any()) }
    }

    @Test
    fun `should return validation error when updated email is malformed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, updateModel(email = "not-an-email"))

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.ClientValidationError)
        coVerify(exactly = 0) { fixture.clientDataSource.updateClient(any(), any()) }
    }

    @Test
    fun `should return error when updated phone belongs to a different client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        val conflictingPhone = "654321"
        val other = Client.Detached.stub(phone = conflictingPhone)
        val requested = updateModel(phone = conflictingPhone)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
            coEvery { clientDataSource.getClient(businessId, conflictingPhone) } returns other
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.exceptionOrNull() is UpdateClient.Error.ClientExist)
        coVerify(exactly = 0) { fixture.clientDataSource.updateClient(any(), any()) }
    }

    @Test
    fun `should allow keeping the client's own phone unchanged`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        val requested = updateModel(phone = existing.phone, description = "notes")
        val updated = existing.copy(description = "notes")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
            coEvery { clientDataSource.getClient(businessId, existing.phone!!) } returns existing
            coEvery { clientDataSource.updateClient(businessId, requested) } returns updated
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when user has read permission only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission(view = true, update = false, delete = false))
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, updateModel(description = "notes"))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientById(any(), any()) }
    }

    @Test
    fun `should return failure when user has no permission for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission.NONE)
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, updateModel(description = "notes"))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientById(any(), any()) }
    }

    @Test
    fun `should assert permission against the requested business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val existing = Client.Detached.stub(id = clientId)
        val requested = updateModel(description = "notes")
        val updated = existing.copy(description = "notes")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { clientDataSource.getClientById(businessId, clientId) } returns existing
            coEvery { clientDataSource.updateClient(businessId, requested) } returns updated
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, requested)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS) }
    }
}
