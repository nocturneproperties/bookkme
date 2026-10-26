package com.bookk.core.data.database

import com.bookk.core.AppLevelConstants
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.Duration.Companion.minutes

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

    return Database.connect(HikariDataSource(poolConfig(schemaName, dbUrl, dbPort, dbUsername, dbPassword, maxPoolSize)))
}

internal fun poolConfig(
    schemaName: String,
    dbUrl: String,
    dbPort: String,
    dbUsername: String,
    dbPassword: String,
    maxPoolSize: Int
): HikariConfig = HikariConfig().apply {
    poolName = schemaName
    jdbcUrl = "jdbc:$dbUrl:$dbPort/$schemaName?rewriteBatchedStatements=true"
    username = dbUsername
    password = dbPassword
    maximumPoolSize = maxPoolSize
    keepaliveTime = IDLE_CONNECTION_KEEPALIVE.inWholeMilliseconds
    isAutoCommit = false
    transactionIsolation = "TRANSACTION_REPEATABLE_READ"
}

private val IDLE_CONNECTION_KEEPALIVE = 5.minutes
