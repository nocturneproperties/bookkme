package com.bookk.core.data.database

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

abstract class BaseUUIDTable(name: String) : UuidTable(name) {
    val createdAt = timestamp("createdAt").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updatedAt").nullable()
}