package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.RejectedClient
import com.bookk.business.domain.api.client.operation.ImportClients
import com.bookk.business.domain.api.error.BusinessErrorCodes
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

internal class ImportClientsImplTest {

    private val requestUserId = Uuid.random()
    private val businessId = Uuid.random()

    private class SutFixture {
        val clientDataSource = mockk<ClientDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = ImportClientsImpl(transactionManager, clientDataSource, businessPermissionDataSource)

        init {
            transactionManager.mockTransaction()
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns
                ResourcePermission.FULL
            coEvery { clientDataSource.getClientsByEmails(any(), any()) } returns emptyList()
            coEvery { clientDataSource.createDetachedClients(any(), any()) } answers { secondArg() }
        }
    }

    @Test
    fun `should create every valid client in one batch`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(
            Client.Detached.stub(name = " Ann ", phone = "+1111", email = null),
            Client.Detached.stub(name = "Ben", phone = null, email = "ben@client.com"),
        )

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients)

        then()
        assertEquals(listOf("Ann", "Ben"), result.getOrThrow().created.map { it.name })
        assertTrue(result.getOrThrow().rejected.isEmpty())
        coVerify(exactly = 1) {
            fixture.clientDataSource.createDetachedClients(
                businessId,
                match {
                    it.map(Client::name) ==
                        listOf("Ann", "Ben")
                },
            )
        }
    }

    @Test
    fun `should look up existing clients once for every email of the import`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(
            Client.Detached.stub(phone = "+1111", email = "a@client.com"),
            Client.Detached.stub(phone = "+2222", email = null),
            Client.Detached.stub(phone = null, email = "b@client.com"),
        )

        whenn()
        fixture.sut(requestUserId, businessId, clients)

        then()
        coVerify(exactly = 1) {
            fixture.clientDataSource.getClientsByEmails(
                businessId,
                match {
                    it.toSet() ==
                        setOf("a@client.com", "b@client.com")
                },
            )
        }
    }

    @Test
    fun `should reject invalid clients by index and create the rest`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(
            Client.Detached.stub(phone = "not a phone", email = null),
            Client.Detached.stub(name = "Valid", phone = "+1111", email = null),
            Client.Detached.stub(phone = null, email = null),
            Client.Detached.stub(phone = null, email = "broken"),
            Client.Detached.stub(name = "A".repeat(MAX_CLIENT_NAME_LENGTH + 1), phone = "+2222", email = null),
        )

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients).getOrThrow()

        then()
        assertEquals(listOf("Valid"), result.created.map { it.name })
        assertEquals(
            listOf(
                RejectedClient(0, BusinessErrorCodes.BUSINESS_CLIENT_NAME_VALIDATION_ERROR),
                RejectedClient(2, BusinessErrorCodes.BUSINESS_CLIENT_MISSING_CONTACT_INFO),
                RejectedClient(3, BusinessErrorCodes.BUSINESS_CLIENT_NAME_VALIDATION_ERROR),
                RejectedClient(4, BusinessErrorCodes.BUSINESS_CLIENT_NAME_VALIDATION_ERROR),
            ),
            result.rejected,
        )
    }

    @Test
    fun `should reject clients whose email already exists in the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(
            Client.Detached.stub(name = "SameEmail", phone = null, email = "taken@client.com"),
            Client.Detached.stub(name = "New", phone = "+3333", email = null),
        )
        coEvery { fixture.clientDataSource.getClientsByEmails(businessId, any()) } returns listOf(
            Client.Integrated.stub(phone = null, email = "TAKEN@client.com"),
        )

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients).getOrThrow()

        then()
        assertEquals(listOf("New"), result.created.map { it.name })
        assertEquals(listOf(RejectedClient(0, BusinessErrorCodes.BUSINESS_CLIENT_EXISTS)), result.rejected)
    }

    @Test
    fun `should create clients sharing a phone with an existing client or with each other`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(
            Client.Detached.stub(name = "Ann", phone = "+1111", email = null),
            Client.Detached.stub(name = "Ben", phone = "+1111", email = null),
        )
        coEvery { fixture.clientDataSource.getClientsByEmails(businessId, any()) } returns emptyList()

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients).getOrThrow()

        then()
        assertEquals(listOf("Ann", "Ben"), result.created.map { it.name })
        assertTrue(result.rejected.isEmpty())
    }

    @Test
    fun `should reject a client repeating the email of an earlier one in the import`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(
            Client.Detached.stub(name = "First", phone = "+1111", email = "first@client.com"),
            Client.Detached.stub(name = "SameEmail", phone = null, email = "first@client.com"),
        )

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients).getOrThrow()

        then()
        assertEquals(listOf("First"), result.created.map { it.name })
        assertEquals(listOf(RejectedClient(1, BusinessErrorCodes.BUSINESS_CLIENT_EXISTS)), result.rejected)
    }

    @Test
    fun `should not write anything when no client is valid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients = listOf(Client.Detached.stub(phone = null, email = null))

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients).getOrThrow()

        then()
        assertTrue(result.created.isEmpty())
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClients(any(), any()) }
    }

    @Test
    fun `should accept an import of exactly the maximum size`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients =
            List(ImportClients.MAX_CLIENTS) { index -> Client.Detached.stub(phone = "+1$index", email = null) }

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients)

        then()
        assertEquals(ImportClients.MAX_CLIENTS, result.getOrThrow().created.size)
    }

    @Test
    fun `should return TooManyClients when the import is larger than the maximum`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clients =
            List(ImportClients.MAX_CLIENTS + 1) { index -> Client.Detached.stub(phone = "+1$index", email = null) }

        whenn()
        val result = fixture.sut(requestUserId, businessId, clients)

        then()
        assertTrue(result.exceptionOrNull() is ImportClients.Error.TooManyClients)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientsByEmails(any(), any()) }
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClients(any(), any()) }
    }

    @Test
    fun `should return ClientExist when a concurrent write takes an email first`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.clientDataSource.createDetachedClients(any(), any()) } throws
            Error.UniqueConstraintFailed("duplicate", IllegalStateException())

        whenn()
        val result = fixture.sut(requestUserId, businessId, listOf(Client.Detached.stub()))

        then()
        assertTrue(result.exceptionOrNull() is ImportClients.Error.ClientExist)
    }

    @Test
    fun `should return failure when user may not update clients`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessPermissionDataSource.getPermission(any(), any(), BusinessResource.CLIENTS) } returns
            ResourcePermission(view = true, update = false, delete = false)

        whenn()
        val result = fixture.sut(requestUserId, businessId, listOf(Client.Detached.stub()))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.createDetachedClients(any(), any()) }
    }
}
