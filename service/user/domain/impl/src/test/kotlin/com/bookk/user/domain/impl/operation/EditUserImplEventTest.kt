package com.bookk.user.domain.impl.operation

import com.bookk.core.data.eventstreaming.impl.kafka.KafkaEventProducer
import com.bookk.core.data.eventstreaming.impl.kafka.KafkaTestBroker
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runIntegrationTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.user.client.api.event.UserEvent
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.datasource.UserDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class EditUserImplEventTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startBroker() {
            KafkaTestBroker.servers
        }
    }

    private class SutFixture {
        val userDataSource = mockk<UserDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = KafkaEventProducer(
            servers = KafkaTestBroker.servers,
            client = "edit-user-test",
            protoBuf = KafkaTestBroker.protoBuf
        )
        val sut = EditUserImpl(userDataSource, transactionManager, eventProducer)
    }


    private fun editModel(phone: String? = null) = UserEditModel(
        id = null, firstName = "Jane", lastName = "Smith", email = "jane@example.com", phone = phone
    )

    private fun publishedFor(userId: Uuid) = KafkaTestBroker
        .awaitRecords(UserEvent.Updated.TOPIC, expected = 1) { it.key() == userId.toString() }
        .map { it.key() to KafkaTestBroker.protoBuf.decodeFromByteArray<UserEvent.Updated>(it.value()) }

    @Test
    fun `should publish an updated profile that other services can decode`() = runIntegrationTest {
        given()
        KafkaTestBroker.createTopic(UserEvent.Updated.TOPIC, partitions = 3)
        val fixture = SutFixture()
        val userId = Uuid.random()
        val edit = editModel(phone = "+10000000000")
        val updated = User(userId, "Jane", "Smith", "jane@example.com", "+10000000000")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, edit, any()) } returns updated
        }

        whenn()
        val published = withContext(Dispatchers.IO) {
            fixture.sut.invoke(userId, edit)
            publishedFor(userId)
        }

        then()
        val (key, event) = published.single()
        assertEquals(userId.toString(), key)
        assertEquals(userId, event.userId)
        assertEquals("Jane", event.name)
        assertEquals("jane@example.com", event.email)
        assertEquals("+10000000000", event.phone)
    }

    @Test
    fun `should publish a profile without a phone over the wire`() = runIntegrationTest {
        given()
        KafkaTestBroker.createTopic(UserEvent.Updated.TOPIC, partitions = 3)
        val fixture = SutFixture()
        val userId = Uuid.random()
        val edit = editModel()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { userDataSource.updateUser(userId, edit, any()) } returns User.stub(id = userId)
        }

        whenn()
        val published = withContext(Dispatchers.IO) {
            fixture.sut.invoke(userId, edit)
            publishedFor(userId)
        }

        then()
        assertNull(published.single().second.phone)
    }
}
