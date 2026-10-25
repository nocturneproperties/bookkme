package com.bookk.core.data

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DataSourceConstraintMappingTest {

    private object UniqueNameTable : Table("unique_name") {
        val name = varchar("name", 64).uniqueIndex()
    }

    private object MissingTable : Table("missing") {
        val id = integer("id")
    }

    private class SutFixture {
        val db = createTestDatabase(UniqueNameTable)
        val sut = object : DataSource() {}
    }

    @Test
    fun `should map a duplicate single-row insert to a unique constraint failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.dbQuery { UniqueNameTable.insert { it[name] = "taken" } } }

        whenn()
        val thrown = runCatching {
            suspendTransaction { fixture.sut.dbQuery { UniqueNameTable.insert { it[name] = "taken" } } }
        }.exceptionOrNull()

        then()
        assertTrue(thrown is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should map a duplicate batch insert to a unique constraint failure`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.dbQuery { UniqueNameTable.insert { it[name] = "taken" } } }

        whenn()
        val thrown = runCatching {
            suspendTransaction {
                fixture.sut.dbQuery { UniqueNameTable.batchInsert(listOf("free", "taken")) { this[UniqueNameTable.name] = it } }
            }
        }.exceptionOrNull()

        then()
        assertTrue(thrown is Error.UniqueConstraintFailed)
    }

    @Test
    fun `should map a failure unrelated to constraints to a database error`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val thrown = runCatching {
            suspendTransaction { fixture.sut.dbQuery { MissingTable.selectAll().toList() } }
        }.exceptionOrNull()

        then()
        assertTrue(thrown is Error.DatabaseError)
    }
}
