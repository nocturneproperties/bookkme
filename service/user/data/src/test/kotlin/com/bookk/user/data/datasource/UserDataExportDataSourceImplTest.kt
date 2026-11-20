package com.bookk.user.data.datasource

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.export.ProfileExport
import io.mockk.mockk
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import library.encryption.test.UnkeyedBlindIndex
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
        val db = createTestDatabase(UserTable)
        val profileCipher = ProfileCipher(InMemorySubjectKeys(), UnkeyedBlindIndex(), RecordingMissingKeys())
        val sut = UserDataExportDataSourceImpl(profileCipher)
        val userSut = UserDataSourceImpl(mockk(relaxed = true), profileCipher)

        suspend fun user(user: User = User.stub(email = "u${Uuid.random()}@example.com")): User = suspendTransaction {
            userSut.insertNewUser(user)
        }
    }

    @Test
    fun `should export the user's profile with its creation time and Telegram user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = fixture.user(
            User.stub(
                name = "Olena",
                lastName = "Koval",
                email = "olena@example.com",
                phone = "+380501112233",
                telegramUserId = 123456789,
            ),
        )
        val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        suspendTransaction { UserTable.update({ UserTable.id eq user.id }) { it[this.createdAt] = createdAt } }

        whenn()
        val profile = suspendTransaction { fixture.sut.getProfile(user.id) }

        then()
        val expected = ProfileExport(
            id = user.id,
            name = "Olena",
            lastName = "Koval",
            email = "olena@example.com",
            phone = "+380501112233",
            createdAt = createdAt,
            telegramUserId = 123456789,
        )
        assertEquals(expected, profile)
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
}
