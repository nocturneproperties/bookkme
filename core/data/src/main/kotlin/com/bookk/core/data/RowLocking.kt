package com.bookk.core.data

import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.SizedIterable

fun Query.forShare(): Query {
    return forUpdate(sharedRowLock())
}

fun <T> SizedIterable<T>.forShare(): SizedIterable<T> {
    return forUpdate(sharedRowLock())
}

private fun sharedRowLock(): ForUpdateOption {
    val supportsSharedRowLocks = currentDialect is MysqlDialect
    return if (supportsSharedRowLocks) ForUpdateOption.MariaDB.LockInShareMode else ForUpdateOption.ForUpdate
}
