package com.bookk.user.data.datasource

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.map.toDomain
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class UserDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(UserTable)
        val cacheClient = InMemoryCacheClient()
        val sut = UserDataSourceImpl(cacheClient)
    }

    @Test
    fun `should insert user and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val user = User.stub()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(user) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNotNull(found)
        assertEquals("Alice", found!!.name)
        assertEquals(user.email, found.email)
    }

    @Test
    fun `should return null when user not found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getUserById(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve user by email`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "find-me@example.com")) }

        whenn()
        val found = suspendTransaction { fixture.sut.getUserByEmail("find-me@example.com") }

        then()
        assertNotNull(found)
        assertEquals("Alice", found!!.name)
    }

    @Test
    fun `should return null when user not found by email`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getUserByEmail("nobody@example.com") }

        then()
        assertNull(found)
    }

    @Test
    fun `should update user name and return true`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub()) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(created.id, UserEditModel(id = null, firstName = "Bob", lastName = null, email = null, phone = null), Clock.System.now())
        }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNotNull(updated)
        assertEquals("Bob", found!!.name)
    }

    @Test
    fun `should round trip an optional phone`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(phone = "+10000000000")) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertEquals("+10000000000", found!!.phone)
    }

    @Test
    fun `should leave phone null when it is not supplied`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub()) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNull(found!!.phone)
    }

    @Test
    fun `should update phone without touching the other fields`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(name = "Alice")) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(created.id, UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "+19999999999"), Clock.System.now())
        }

        then()
        assertEquals("+19999999999", updated!!.phone)
        assertEquals("Alice", updated.name)
    }

    @Test
    fun `should store a blank phone as null on insert`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(phone = " ")) }

        then()
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }
        assertNull(found!!.phone)
    }

    @Test
    fun `should clear the phone when a blank one is supplied`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub(phone = "+19999999999")) }

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(created.id, UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "  "), Clock.System.now())
        }

        then()
        assertNull(updated!!.phone)
    }

    @Test
    fun `should allow several users without a phone after clearing it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val first = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "first@example.com", phone = "+11111111111")) }
        val second = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "second@example.com", phone = "+12222222222")) }
        val clearPhone = UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "")

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.updateUser(first.id, clearPhone, Clock.System.now())
                fixture.sut.updateUser(second.id, clearPhone, Clock.System.now())
            }
        }

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should reject a phone already used by another user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "owner@example.com", phone = "+19999999999")) }
        val other = suspendTransaction { fixture.sut.insertNewUser(User.stub(email = "other@example.com")) }

        whenn()
        val result = runCatching {
            suspendTransaction {
                fixture.sut.updateUser(other.id, UserEditModel(id = null, firstName = null, lastName = null, email = null, phone = "+19999999999"), Clock.System.now())
            }
        }

        then()
        assertTrue(result.exceptionOrNull()?.toDomain() is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should return null when updating non-existent user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val updated = suspendTransaction {
            fixture.sut.updateUser(Uuid.random(), UserEditModel(id = null, firstName = "Bob", lastName = null, email = null, phone = null), Clock.System.now())
        }

        then()
        assertNull(updated)
    }

    @Test
    fun `should delete user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val created = suspendTransaction { fixture.sut.insertNewUser(User.stub()) }

        whenn()
        suspendTransaction { fixture.sut.deleteUser(created.id) }
        val found = suspendTransaction { fixture.sut.getUserById(created.id) }

        then()
        assertNull(found)
    }
}
