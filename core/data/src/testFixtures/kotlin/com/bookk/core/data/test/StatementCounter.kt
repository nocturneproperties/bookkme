package com.bookk.core.data.test

import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

private class StatementCounter : StatementInterceptor {
    var count: Int = 0
        private set

    override fun afterExecution(
        transaction: Transaction,
        contexts: List<StatementContext>,
        executedStatement: PreparedStatementApi,
    ) {
        count++
    }
}

suspend fun JdbcTransaction.countStatements(block: suspend () -> Unit): Int {
    val counter = StatementCounter()
    registerInterceptor(counter)
    try {
        block()
    } finally {
        unregisterInterceptor(counter)
    }
    return counter.count
}
