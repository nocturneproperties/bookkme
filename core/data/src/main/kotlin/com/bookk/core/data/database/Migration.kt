package com.bookk.core.data.database

import com.bookk.core.AppLevelConstants
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.jetbrains.exposed.v1.jdbc.Database

fun createDatabase(
    schemaName: String,
    dbUrl: String = AppLevelConstants.dbUrl,
    dbPort: String = AppLevelConstants.dbPort,
    dbUsername: String = AppLevelConstants.dbUsername,
    dbPassword: String = AppLevelConstants.dbPassword,
    maxPoolSize: Int = AppLevelConstants.dbPoolSize
): Database {
    Flyway.configure()
        .dataSource(
            "jdbc:$dbUrl:$dbPort/$schemaName?createDatabaseIfNotExist=true",
            dbUsername,
            dbPassword
        )
        .baselineOnMigrate(true)
        .createSchemas(true)
        .defaultSchema(schemaName)
        .executeInTransaction(true)
        .locations(Location("db/migration/$schemaName"))
        .load()
        .migrate()

    val pool = HikariConfig().apply {
        poolName = schemaName
        jdbcUrl = "jdbc:$dbUrl:$dbPort/$schemaName?rewriteBatchedStatements=true"
        username = dbUsername
        password = dbPassword
        maximumPoolSize = maxPoolSize
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    }
    return Database.connect(HikariDataSource(pool))
}
