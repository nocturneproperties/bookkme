package com.bookk.core.data.test

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun createTestDatabase(vararg tables: Table): Database {
    val db = Database.connect(
        url = "jdbc:h2:mem:test_${System.nanoTime()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver"
    )
    transaction(db) { SchemaUtils.create(*tables) }
    TransactionManager.defaultDatabase = db
    return db
}
