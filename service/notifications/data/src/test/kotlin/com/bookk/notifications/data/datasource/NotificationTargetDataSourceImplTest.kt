package com.bookk.notifications.data.datasource

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.data.orm.table.NotificationEmailTargetTable
import com.bookk.notifications.data.orm.table.NotificationTelegramTargetTable
import library.encryption.Decrypted
import library.encryption.test.InMemorySubjectKeys
import library.encryption.test.RecordingMissingKeys
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class NotificationTargetDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(NotificationEmailTargetTable, NotificationTelegramTargetTable)
        val subjectKeys = InMemorySubjectKeys()
        val missingKeys = RecordingMissingKeys()
        val sut = NotificationTargetDataSourceImpl(TargetCipher(subjectKeys, missingKeys))

        suspend fun insertEmail(userId: Uuid, email: String) {
            suspendTransaction { sut.insertEmail(userId, email, Instant.fromEpochMilliseconds(0)) }
        }

        suspend fun insertTelegram(userId: Uuid, tag: String) {
            suspendTransaction { sut.insertTelegram(userId, tag) }
        }

        suspend fun insertPlaintextEmail(userId: Uuid, email: String) {
            suspendTransaction {
                NotificationEmailTargetTable.insert {
                    it[NotificationEmailTargetTable.userId] = userId
                    it[NotificationEmailTargetTable.email] = email
                }
            }
        }

        suspend fun insertPlaintextTelegram(userId: Uuid, tag: String) {
            suspendTransaction {
                NotificationTelegramTargetTable.insert {
                    it[NotificationTelegramTargetTable.userId] = userId
                    it[NotificationTelegramTargetTable.telegramTag] = tag
                }
            }
        }

        suspend fun emailRow(userId: Uuid): ResultRow? {
            return suspendTransaction {
                NotificationEmailTargetTable.selectAll()
                    .where { NotificationEmailTargetTable.userId eq userId }
                    .singleOrNull()
            }
        }

        suspend fun telegramRow(userId: Uuid): ResultRow? {
            return suspendTransaction {
                NotificationTelegramTargetTable.selectAll()
                    .where { NotificationTelegramTargetTable.userId eq userId }
                    .singleOrNull()
            }
        }

        suspend fun decrypted(userId: Uuid, ciphertext: ByteArray?): String? {
            val decryptor = subjectKeys.decryptor(listOf(userId))
            return ciphertext?.let { (decryptor.decrypt(userId, it) as Decrypted.Plain).value.decodeToString() }
        }
    }

    @Test
    fun `should retrieve email for user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertEmail(userId, "user@example.com")

        whenn()
        val email = suspendTransaction { fixture.sut.getEmail(userId) }

        then()
        assertEquals("user@example.com", email)
    }

    @Test
    fun `should return null email for unknown user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val email = suspendTransaction { fixture.sut.getEmail(Uuid.random()) }

        then()
        assertNull(email)
    }

    @Test
    fun `should store the email encrypted under the user's key and no plaintext`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        fixture.insertEmail(userId, "user@example.com")

        then()
        val row = checkNotNull(fixture.emailRow(userId))
        assertNull(row[NotificationEmailTargetTable.email])
        assertEquals("user@example.com", fixture.decrypted(userId, row[NotificationEmailTargetTable.emailEncrypted]))
    }

    @Test
    fun `should read an email stored as plaintext before encryption`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertPlaintextEmail(userId, "legacy@example.com")

        whenn()
        val email = suspendTransaction { fixture.sut.getEmail(userId) }

        then()
        assertEquals("legacy@example.com", email)
    }

    @Test
    fun `should read no email for a user whose key is shredded without reporting it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertEmail(userId, "user@example.com")
        fixture.subjectKeys.shred(userId)

        whenn()
        val email = suspendTransaction { fixture.sut.getEmail(userId) }

        then()
        assertNull(email)
        assertTrue(fixture.missingKeys.reported.isEmpty())
    }

    @Test
    fun `should read no email and report the table when the user's key is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertEmail(userId, "user@example.com")
        fixture.subjectKeys.loseKey(userId)

        whenn()
        val email = suspendTransaction { fixture.sut.getEmail(userId) }

        then()
        assertNull(email)
        assertEquals(listOf("notification_email_targets"), fixture.missingKeys.reported)
    }

    @Test
    fun `should report an email target that exists even when its key is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertEmail(userId, "user@example.com")
        fixture.subjectKeys.loseKey(userId)

        whenn()
        val exists = suspendTransaction { fixture.sut.hasEmail(userId) }

        then()
        assertTrue(exists)
    }

    @Test
    fun `should report no email target for a user without one`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val exists = suspendTransaction { fixture.sut.hasEmail(Uuid.random()) }

        then()
        assertFalse(exists)
    }

    @Test
    fun `should insert email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        suspendTransaction {
            fixture.sut.insertEmail(userId, "inserted@example.com", Instant.fromEpochMilliseconds(1000))
        }

        then()
        val email = suspendTransaction { fixture.sut.getEmail(userId) }
        assertEquals("inserted@example.com", email)
    }

    @Test
    fun `should skip inserting the email of a user whose key is shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.subjectKeys.shred(userId)

        whenn()
        suspendTransaction {
            fixture.sut.insertEmail(userId, "erased@example.com", Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertNull(fixture.emailRow(userId))
    }

    @Test
    fun `should update existing email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertEmail(userId, "old@example.com")

        whenn()
        val applied = suspendTransaction {
            fixture.sut.updateEmail(userId, "new@example.com", Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertTrue(applied)
        assertEquals("new@example.com", suspendTransaction { fixture.sut.getEmail(userId) })
    }

    @Test
    fun `should encrypt a plaintext email row when updating it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertPlaintextEmail(userId, "legacy@example.com")

        whenn()
        suspendTransaction { fixture.sut.updateEmail(userId, "new@example.com", Instant.fromEpochMilliseconds(1000)) }

        then()
        val row = checkNotNull(fixture.emailRow(userId))
        assertNull(row[NotificationEmailTargetTable.email])
        assertEquals("new@example.com", fixture.decrypted(userId, row[NotificationEmailTargetTable.emailEncrypted]))
    }

    @Test
    fun `should not update the email of a user whose key is shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertPlaintextEmail(userId, "legacy@example.com")
        fixture.subjectKeys.shred(userId)

        whenn()
        val applied = suspendTransaction {
            fixture.sut.updateEmail(userId, "new@example.com", Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertFalse(applied)
        assertEquals("legacy@example.com", fixture.emailRow(userId)?.get(NotificationEmailTargetTable.email))
    }

    @Test
    fun `should report no email row updated when none exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val applied = suspendTransaction {
            fixture.sut.updateEmail(userId, "new@example.com", Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertFalse(applied)
        assertNull(suspendTransaction { fixture.sut.getEmail(userId) })
    }

    @Test
    fun `should ignore an email update older than the one already applied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.insertEmail(userId, "newer@example.com", Instant.fromEpochMilliseconds(2000)) }

        whenn()
        val applied = suspendTransaction {
            fixture.sut.updateEmail(userId, "older@example.com", Instant.fromEpochMilliseconds(1000))
        }

        then()
        assertFalse(applied)
        assertEquals("newer@example.com", suspendTransaction { fixture.sut.getEmail(userId) })
    }

    @Test
    fun `should apply an email update newer than the one already applied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.insertEmail(userId, "first@example.com", Instant.fromEpochMilliseconds(1000)) }

        whenn()
        val applied = suspendTransaction {
            fixture.sut.updateEmail(userId, "second@example.com", Instant.fromEpochMilliseconds(2000))
        }

        then()
        assertTrue(applied)
        assertEquals("second@example.com", suspendTransaction { fixture.sut.getEmail(userId) })
    }

    @Test
    fun `should retrieve telegram tag for user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertTelegram(userId, "@myuser")

        whenn()
        val telegram = suspendTransaction { fixture.sut.getTelegram(userId) }

        then()
        assertEquals("@myuser", telegram)
    }

    @Test
    fun `should return null telegram for unknown user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val telegram = suspendTransaction { fixture.sut.getTelegram(Uuid.random()) }

        then()
        assertNull(telegram)
    }

    @Test
    fun `should store the telegram tag encrypted under the user's key and no plaintext`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        fixture.insertTelegram(userId, "@myuser")

        then()
        val row = checkNotNull(fixture.telegramRow(userId))
        assertNull(row[NotificationTelegramTargetTable.telegramTag])
        assertEquals("@myuser", fixture.decrypted(userId, row[NotificationTelegramTargetTable.telegramTagEncrypted]))
    }

    @Test
    fun `should read a telegram tag stored as plaintext before encryption`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertPlaintextTelegram(userId, "@legacy")

        whenn()
        val telegram = suspendTransaction { fixture.sut.getTelegram(userId) }

        then()
        assertEquals("@legacy", telegram)
    }

    @Test
    fun `should read no telegram tag and report the table when the user's key is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertTelegram(userId, "@myuser")
        fixture.subjectKeys.loseKey(userId)

        whenn()
        val telegram = suspendTransaction { fixture.sut.getTelegram(userId) }

        then()
        assertNull(telegram)
        assertEquals(listOf("notification_telegram_targets"), fixture.missingKeys.reported)
    }

    @Test
    fun `should read no telegram tag for a user whose key is shredded without reporting it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertTelegram(userId, "@myuser")
        fixture.subjectKeys.shred(userId)

        whenn()
        val telegram = suspendTransaction { fixture.sut.getTelegram(userId) }

        then()
        assertNull(telegram)
        assertTrue(fixture.missingKeys.reported.isEmpty())
    }

    @Test
    fun `should report whether a telegram target exists regardless of its key`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertTelegram(userId, "@myuser")
        fixture.subjectKeys.loseKey(userId)

        whenn()
        val exists = suspendTransaction { fixture.sut.hasTelegram(userId) }
        val unknownExists = suspendTransaction { fixture.sut.hasTelegram(Uuid.random()) }

        then()
        assertTrue(exists)
        assertFalse(unknownExists)
    }

    @Test
    fun `should insert telegram`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.insertTelegram(userId, "@inserted") }

        then()
        val telegram = suspendTransaction { fixture.sut.getTelegram(userId) }
        assertEquals("@inserted", telegram)
    }

    @Test
    fun `should skip inserting the telegram tag of a user whose key is shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.subjectKeys.shred(userId)

        whenn()
        suspendTransaction { fixture.sut.insertTelegram(userId, "@erased") }

        then()
        assertNull(fixture.telegramRow(userId))
    }

    @Test
    fun `should update existing telegram`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertTelegram(userId, "@old")

        whenn()
        val applied = suspendTransaction { fixture.sut.updateTelegram(userId, "@new") }

        then()
        assertTrue(applied)
        val telegram = suspendTransaction { fixture.sut.getTelegram(userId) }
        assertEquals("@new", telegram)
    }

    @Test
    fun `should encrypt a plaintext telegram row when updating it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertPlaintextTelegram(userId, "@legacy")

        whenn()
        suspendTransaction { fixture.sut.updateTelegram(userId, "@new") }

        then()
        val row = checkNotNull(fixture.telegramRow(userId))
        assertNull(row[NotificationTelegramTargetTable.telegramTag])
        assertEquals("@new", fixture.decrypted(userId, row[NotificationTelegramTargetTable.telegramTagEncrypted]))
    }

    @Test
    fun `should not update the telegram tag of a user whose key is shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertPlaintextTelegram(userId, "@legacy")
        fixture.subjectKeys.shred(userId)

        whenn()
        val applied = suspendTransaction { fixture.sut.updateTelegram(userId, "@new") }

        then()
        assertFalse(applied)
        assertEquals("@legacy", fixture.telegramRow(userId)?.get(NotificationTelegramTargetTable.telegramTag))
    }

    @Test
    fun `should encrypt plaintext email rows up to the limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userIds = List(3) { Uuid.random() }
        userIds.forEach { fixture.insertPlaintextEmail(it, "$it@example.com") }

        whenn()
        val encrypted = suspendTransaction { fixture.sut.encryptPlaintextEmails(limit = 2) }

        then()
        assertEquals(2, encrypted)
        val rows = userIds.map { checkNotNull(fixture.emailRow(it)) }
        assertEquals(2, rows.count { it[NotificationEmailTargetTable.email] == null })
        rows.filter { it[NotificationEmailTargetTable.email] == null }.forEach { row ->
            val userId = row[NotificationEmailTargetTable.userId]
            assertEquals(
                "$userId@example.com",
                fixture.decrypted(userId, row[NotificationEmailTargetTable.emailEncrypted]),
            )
        }
    }

    @Test
    fun `should leave encrypted email rows alone when encrypting plaintext ones`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertEmail(userId, "user@example.com")

        whenn()
        val encrypted = suspendTransaction { fixture.sut.encryptPlaintextEmails(limit = 100) }

        then()
        assertEquals(0, encrypted)
        assertEquals("user@example.com", suspendTransaction { fixture.sut.getEmail(userId) })
    }

    @Test
    fun `should fail to encrypt a plaintext email row whose user's key is shredded`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertPlaintextEmail(userId, "legacy@example.com")
        fixture.subjectKeys.shred(userId)

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.encryptPlaintextEmails(limit = 100) } }

        then()
        assertTrue(result.exceptionOrNull() is Error.UnknownError) { result.toString() }
    }

    @Test
    fun `should encrypt plaintext telegram rows up to the limit`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userIds = List(3) { Uuid.random() }
        userIds.forEach { fixture.insertPlaintextTelegram(it, "@$it") }

        whenn()
        val encrypted = suspendTransaction { fixture.sut.encryptPlaintextTelegramTags(limit = 2) }

        then()
        assertEquals(2, encrypted)
        val rows = userIds.map { checkNotNull(fixture.telegramRow(it)) }
        val encryptedRows = rows.filter { it[NotificationTelegramTargetTable.telegramTag] == null }
        assertEquals(2, encryptedRows.size)
        encryptedRows.forEach { row ->
            val userId = row[NotificationTelegramTargetTable.userId]
            val ciphertext = row[NotificationTelegramTargetTable.telegramTagEncrypted]
            assertEquals("@$userId", fixture.decrypted(userId, ciphertext))
        }
    }

    @Test
    fun `should leave encrypted telegram rows alone when encrypting plaintext ones`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertTelegram(userId, "@myuser")

        whenn()
        val encrypted = suspendTransaction { fixture.sut.encryptPlaintextTelegramTags(limit = 100) }

        then()
        assertEquals(0, encrypted)
        assertEquals("@myuser", suspendTransaction { fixture.sut.getTelegram(userId) })
    }

    @Test
    fun `should delete both email and telegram targets for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertEmail(userId, "user@example.com")
        fixture.insertTelegram(userId, "@user")

        whenn()
        suspendTransaction { fixture.sut.deleteByUserId(userId) }

        then()
        assertNull(fixture.emailRow(userId))
        assertNull(fixture.telegramRow(userId))
    }

    @Test
    fun `should not fail deleting targets for a user with none`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.deleteByUserId(Uuid.random()) } }

        then()
        assertTrue(result.isSuccess)
    }
}
