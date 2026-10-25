package com.bookk.appointments.data.datasource

import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext

internal class CapturingSqlLogger : SqlLogger {
    val statements = mutableListOf<String>()

    override fun log(context: StatementContext, transaction: Transaction) {
        statements += context.sql(transaction)
    }
}
