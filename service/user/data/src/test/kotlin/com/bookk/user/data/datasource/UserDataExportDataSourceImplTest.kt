package com.bookk.user.data.datasource

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.data.orm.table.ContactFormTable
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.ContactForm
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.export.ContactMessageExport
import com.bookk.user.domain.api.export.ProfileExport
import io.mockk.mockk
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UserDataExportDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(UserTable, ContactFormTable)
        val sut = UserDataExportDataSourceImpl()
        val userSut = UserDataSourceImpl(mockk(relaxed = true))
        val communicationSut = CommunicationDataSourceImpl()

        suspend fun user(user: User = User.stub(email = "u${Uuid.random()}@example.com")): User = suspendTransaction { userSut.insertNewUser(user) }

        suspend fun message(userId: Uuid, text: String, sentAt: Instant) {
            suspendTransaction { communicationSut.saveContactForm(ContactForm(userId, text, usageLogs = "app 1.4.2", status = ContactForm.ContactFormStatus.DONE)) }
            suspendTransaction { ContactFormTable.update({ ContactFormTable.text eq text }) { it[createdAt] = sentAt } }
        }
    }

    @Test
    fun `should export the user's profile with its creation time`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = fixture.user(User.stub(name = "Olena", lastName = "Koval", email = "olena@example.com", phone = "+380501112233"))
        val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        suspendTransaction { UserTable.update({ UserTable.id eq user.id }) { it[this.createdAt] = createdAt } }

        whenn()
        val profile = suspendTransaction { fixture.sut.getProfile(user.id) }

        then()
        assertEquals(ProfileExport(user.id, "Olena", "Koval", "olena@example.com", "+380501112233", createdAt), profile)
    }

    @Test
    fun `should export no profile for an unknown user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val profile = suspendTransaction { fixture.sut.getProfile(Uuid.random()) }

        then()
        assertNull(profile)
    }

    @Test
    fun `should export the user's contact messages oldest first, and no one else's`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = fixture.user()
        val other = fixture.user()
        fixture.message(user.id, "second", Instant.fromEpochMilliseconds(2_000))
        fixture.message(user.id, "first", Instant.fromEpochMilliseconds(1_000))
        fixture.message(other.id, "not mine", Instant.fromEpochMilliseconds(500))

        whenn()
        val messages = suspendTransaction { fixture.sut.getContactMessages(user.id) }

        then()
        assertEquals(
            listOf(
                ContactMessageExport("first", "app 1.4.2", "DONE", Instant.fromEpochMilliseconds(1_000)),
                ContactMessageExport("second", "app 1.4.2", "DONE", Instant.fromEpochMilliseconds(2_000)),
            ),
            messages,
        )
    }
}
