package com.bookk.auth.data.orm.table

import com.bookk.auth.domain.api.authentication.entity.DormancyStage
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

internal object AuthenticationTable : UuidTable("authentication") {
    val userId = uuid("user_id").uniqueIndex()
    val uuid = uuid("uuid").uniqueIndex()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    val lastActiveAt = timestamp("last_active_at").clientDefault { Clock.System.now() }.index()
    val dormancyStage = enumeration("dormancy_stage", DormancyStage::class).default(DormancyStage.ACTIVE)
    val dormancyWarnedAt = timestamp("dormancy_warned_at").nullable()
    val deletionHold = bool("deletion_hold").default(false)
}
