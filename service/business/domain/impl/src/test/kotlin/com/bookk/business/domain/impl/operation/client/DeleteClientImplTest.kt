package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
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
import library.actionconfirmation.ActionConfirmationGrant
import library.actionconfirmation.test.ActionConfirmationTestTokens
import library.encryption.test.ShredRecordingSubjectKeys
import library.export.DataExports
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteClientImplTest {

    private class SutFixture(val businessId: Uuid = Uuid.random(), val requestUserId: Uuid = Uuid.random()) {
        val clientDataSource = mockk<ClientDataSource>()
        val erasedClientDataSource = mockk<ErasedClientDataSource>()
        val dataExports = mockk<DataExports>()
        val recordedInTransaction = mutableListOf<Boolean>()
        val discardedInTransaction = mutableListOf<Boolean>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val businessDataSource = mockk<BusinessDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val sentEvents = mutableListOf<BusinessEvent.ClientDeleted>()
        val sentInTransaction = mutableListOf<Boolean>()
        val confirmationTokens = ActionConfirmationTestTokens()
        val tokenSpentWhenLocking = mutableListOf<Boolean>()
        val subjectKeys = ShredRecordingSubjectKeys(transactionManager::isInTransaction)
        val sut = DeleteClientImpl(
            transactionManager,
            clientDataSource,
            businessPermissionDataSource,
            businessDataSource,
            eventProducer,
            confirmationTokens.gate,
            erasedClientDataSource,
            dataExports,
            subjectKeys,
        )

        init {
            transactionManager.mockTransaction()
            grantPermission(ResourcePermission.FULL)
            coEvery { businessDataSource.isOwner(requestUserId, businessId) } returns false
            coEvery { clientDataSource.deleteClient(businessId, any()) } returns true
            coEvery { erasedClientDataSource.record(businessId, any()) } coAnswers {
                recordedInTransaction += transactionManager.isInTransaction()
            }
            coEvery { dataExports.discard(businessId) } coAnswers {
                discardedInTransaction += transactionManager.isInTransaction()
            }
            coEvery { eventProducer.send(any<BusinessEvent.ClientDeleted>(), any()) } coAnswers {
                sentEvents += firstArg<BusinessEvent.ClientDeleted>()
                sentInTransaction += transactionManager.isInTransaction()
            }
        }

        fun grantPermission(permission: ResourcePermission) {
            coEvery {
                businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
            } returns
                permission
        }

        fun stored(client: Client?, id: Uuid = client?.id ?: Uuid.random()): Uuid {
            coEvery { clientDataSource.getClientForUpdate(businessId, id) } coAnswers {
                tokenSpentWhenLocking += confirmationTokens.isSpent(TOKEN_ID)
                client
            }
            return id
        }

        fun tokenFor(clientId: Uuid): String = confirmationTokens.tokenFor(
            ActionConfirmationGrant.clientDeletion(requestUserId, businessId, clientId),
            tokenId = TOKEN_ID,
        )

        fun makeOwner() {
            coEvery { businessDataSource.isOwner(requestUserId, businessId) } returns true
        }
    }

    @Test
    fun `should delete a client without an account and announce it inside the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.clientDataSource.deleteClient(fixture.businessId, id) }
        assertEquals(
            listOf(BusinessEvent.ClientDeleted(fixture.businessId, id, idempotencyKey = "")),
            fixture.sentEvents.map {
                it.copy(idempotencyKey = "")
            },
        )
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should record the deletion inside the transaction so a restore can run it again`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        coVerify(exactly = 1) { fixture.erasedClientDataSource.record(fixture.businessId, id) }
        assertEquals(listOf(true), fixture.recordedInTransaction)
    }

    @Test
    fun `should discard the business's data export inside the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        coVerify(exactly = 1) { fixture.dataExports.discard(fixture.businessId) }
        assertEquals(listOf(true), fixture.discardedInTransaction)
    }

    @Test
    fun `should discard no data export when the client does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(null)

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        coVerify(exactly = 0) { fixture.dataExports.discard(any()) }
    }

    @Test
    fun `should shred the client's key inside the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertEquals(setOf(id), fixture.subjectKeys.shreddedInTransaction)
    }

    @Test
    fun `should shred no key when the client does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(null)

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertEquals(emptySet<Uuid>(), fixture.subjectKeys.shredded)
    }

    @Test
    fun `should record nothing when the client does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(null)

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        coVerify(exactly = 0) { fixture.erasedClientDataSource.record(any(), any()) }
    }

    @Test
    fun `should lock the client before checking the permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        coVerifyOrder {
            fixture.clientDataSource.getClientForUpdate(fixture.businessId, id)
            fixture.businessPermissionDataSource.getPermission(
                fixture.requestUserId,
                fixture.businessId,
                BusinessResource.CLIENTS,
            )
            fixture.clientDataSource.deleteClient(fixture.businessId, id)
        }
    }

    @Test
    fun `should return failure when the client does not exist in the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(null)

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.exceptionOrNull() is DeleteClient.Error.NotFound)
        coVerify(exactly = 0) { fixture.clientDataSource.deleteClient(any(), any()) }
        assertTrue(fixture.sentEvents.isEmpty())
    }

    @Test
    fun `should return failure when user has read permission only`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())
        fixture.grantPermission(ResourcePermission(view = true, update = false, delete = false))

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.deleteClient(any(), any()) }
        assertTrue(fixture.sentEvents.isEmpty())
    }

    @Test
    fun `should not reveal whether the client exists to a caller without permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(null)
        fixture.grantPermission(ResourcePermission.NONE)

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should let the owner delete a client with an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Integrated.stub())
        fixture.makeOwner()

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.clientDataSource.deleteClient(fixture.businessId, id) }
        assertEquals(listOf(id), fixture.sentEvents.map { it.clientId })
    }

    @Test
    fun `should not let an employee with the delete grant delete a client with an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Integrated.stub())

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.clientDataSource.deleteClient(any(), any()) }
        assertTrue(fixture.sentEvents.isEmpty())
    }

    @Test
    fun `should let an employee with the delete grant delete a client without an account`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.businessDataSource.isOwner(any(), any()) }
    }

    @Test
    fun `should refuse to delete without a valid action confirmation token for this client`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(Uuid.random()))

        then()
        assertTrue(result.exceptionOrNull() is DeleteClient.Error.ActionConfirmationInvalid)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientForUpdate(any(), any()) }
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
        assertTrue(fixture.sentEvents.isEmpty())
    }

    @Test
    fun `should refuse an action confirmation token that was already used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())
        val token = fixture.tokenFor(id)
        fixture.sut(fixture.requestUserId, fixture.businessId, id, token)

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, token)

        then()
        assertTrue(result.exceptionOrNull() is DeleteClient.Error.ActionConfirmationInvalid)
        coVerify(exactly = 1) { fixture.clientDataSource.deleteClient(any(), any()) }
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should spend the action confirmation token before deleting and keep it spent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(Client.Detached.stub())

        whenn()
        fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertEquals(listOf(true), fixture.tokenSpentWhenLocking)
        assertTrue(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    @Test
    fun `should give the action confirmation token back when the deletion fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val id = fixture.stored(null)

        whenn()
        val result = fixture.sut(fixture.requestUserId, fixture.businessId, id, fixture.tokenFor(id))

        then()
        assertTrue(result.exceptionOrNull() is DeleteClient.Error.NotFound)
        assertFalse(fixture.confirmationTokens.isSpent(TOKEN_ID))
    }

    private companion object {
        const val TOKEN_ID = "token-id"
    }
}
