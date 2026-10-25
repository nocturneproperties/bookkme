package com.bookk.business.data.orm.table

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.experimental.or
import kotlin.time.Clock

private val MONDAY_TO_FRIDAY: Byte = DayOfWeek.entries
    .filter { it < DayOfWeek.SATURDAY }
    .fold(0) { acc, day -> acc or (1 shl day.isoDayNumber).toByte() }

object BusinessTable: UuidTable("business") {
    val userId = uuid("user_id").uniqueIndex()
    val name = varchar("name", 512)
    val description = varchar("description", 1024)
    val address = varchar("address", 512)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val currency = varchar("currency", 3)
    val timezone = varchar("timezone", 255)
    val workingDays = byte("working_days").default(MONDAY_TO_FRIDAY)
    val instagram = varchar("instagram", 255).nullable()
    val telegram = varchar("telegram", 255).nullable()
    val viber = varchar("viber", 255).nullable()
    val whatsapp = varchar("whatsapp", 255).nullable()
    val phone = varchar("phone", 255).nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}