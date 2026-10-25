package com.bookk.notifications.data.datasource

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.data.orm.entity.NotificationEmailTargetEntity
import com.bookk.notifications.data.orm.entity.NotificationTelegramTargetEntity
import com.bookk.notifications.data.orm.table.NotificationEmailTargetTable
import com.bookk.notifications.data.orm.table.NotificationTelegramTargetTable
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
        val sut = NotificationTargetDataSourceImpl()

        suspend fun insertEmail(userId: Uuid, email: String) {
            suspendTransaction {
                NotificationEmailTargetEntity.new {
                    this.userId = userId
                    this.email = email
                }
            }
        }

        suspend fun insertTelegram(userId: Uuid, tag: String) {
            suspendTransaction {
                NotificationTelegramTargetEntity.new {
                    this.userId = userId
                    this.telegramTag = tag
                }
            }
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
    fun `should insert email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.insertEmail(userId, "inserted@example.com", Instant.fromEpochMilliseconds(1000)) }

        then()
        val email = suspendTransaction { fixture.sut.getEmail(userId) }
        assertEquals("inserted@example.com", email)
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
    fun `should update existing telegram`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertTelegram(userId, "@old")

        whenn()
        suspendTransaction { fixture.sut.updateTelegram(userId, "@new") }

        then()
        val telegram = suspendTransaction { fixture.sut.getTelegram(userId) }
        assertEquals("@new", telegram)
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
        assertNull(suspendTransaction { fixture.sut.getEmail(userId) })
        assertNull(suspendTransaction { fixture.sut.getTelegram(userId) })
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
