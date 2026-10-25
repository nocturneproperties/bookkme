package com.bookk.core.data

import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.jdbc.Query

fun Query.forShare(): Query {
    val supportsSharedRowLocks = currentDialect is MysqlDialect
    return forUpdate(if (supportsSharedRowLocks) ForUpdateOption.MariaDB.LockInShareMode else ForUpdateOption.ForUpdate)
}
