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
import io.mockk.mockk
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class CommunicationDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(UserTable, ContactFormTable)
        val sut = CommunicationDataSourceImpl()
        val userSut = UserDataSourceImpl(mockk(relaxed = true))

        suspend fun insertUser(): Uuid {
            return suspendTransaction {
                userSut.insertNewUser(User(Uuid.random(), "Test", "User", "test${Uuid.random()}@example.com", null))
            }.id
        }
    }

    @Test
    fun `should save contact form without throwing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = fixture.insertUser()
        val form = ContactForm(
            userId = userId,
            text = "Help me please",
            usageLogs = null,
            status = ContactForm.ContactFormStatus.NEW
        )

        whenn()
        suspendTransaction { fixture.sut.saveContactForm(form) }

        then()
        val count = suspendTransaction {
            ContactFormTable.selectAll()
                .where { ContactFormTable.userId eq userId }
                .count()
        }
        assertEquals(1L, count)
    }

    @Test
    fun `should save contact form with usage logs`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = fixture.insertUser()
        val form = ContactForm(
            userId = userId,
            text = "Something broke",
            usageLogs = "crash log here",
            status = ContactForm.ContactFormStatus.NEW
        )

        whenn()
        suspendTransaction { fixture.sut.saveContactForm(form) }

        then()
        val count = suspendTransaction {
            ContactFormTable.selectAll()
                .where { ContactFormTable.userId eq userId }
                .count()
        }
        assertEquals(1L, count)
    }
}
