package com.bookk.auth.data.orm.table

import org.jetbrains.exposed.v1.core.Table

internal object DemoSettingsTable : Table("demo_settings") {
    const val ROW_ID: Byte = 1

    val id = byte("id")
    val maxActiveAccounts = integer("max_active_accounts").nullable()

    override val primaryKey = PrimaryKey(id)
}
