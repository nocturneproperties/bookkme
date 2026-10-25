package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AccountDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(AuthenticationTable)
        val sut = AccountDataSourceImpl()
    }

    @Test
    fun `should create authorization and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()

        whenn()
        val created = suspendTransaction { fixture.sut.createAuthorization(auth) }
        val found = suspendTransaction { fixture.sut.getAuthRecordById(created.id) }

        then()
        assertNotNull(found)
        assertEquals(auth.userId, found!!.userId)
        assertEquals(auth.uuid, found.uuid)
    }

    @Test
    fun `should return null when auth record not found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordById(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve auth record by uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()
        suspendTransaction { fixture.sut.createAuthorization(auth) }

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUUID(auth.uuid) }

        then()
        assertNotNull(found)
        assertEquals(auth.userId, found!!.userId)
    }

    @Test
    fun `should return null when auth record not found by uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUUID(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve auth record by userId`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()
        suspendTransaction { fixture.sut.createAuthorization(auth) }

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUserId(auth.userId) }

        then()
        assertNotNull(found)
        assertEquals(auth.uuid, found!!.uuid)
    }

    @Test
    fun `should return null when auth record not found by userId`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getAuthRecordByUserId(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should delete authorization`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = Authentication.stub()
        val created = suspendTransaction { fixture.sut.createAuthorization(auth) }

        whenn()
        suspendTransaction { fixture.sut.deleteAuthorization(created.id) }
        val found = suspendTransaction { fixture.sut.getAuthRecordById(created.id) }

        then()
        assertNull(found)
    }
}
