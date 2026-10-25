package com.bookk.auth.data.orm.table

import com.bookk.core.domain.entity.Language
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

internal object AuthDeviceTable : UuidTable("auth_device") {
    val userAuthId = reference("user_auth_id", AuthenticationTable, onDelete = ReferenceOption.CASCADE).index()
    val deviceUUID = uuid("device_uuid")
    val deviceName = varchar("device_name", 255)
    val language = enumeration("language", Language::class)
    val refreshTokenId = uuid("refresh_token_id").nullable()
    val refreshTokenHash = varchar("refresh_token_hash", 64).nullable()
    val refreshTokenExpiresAt = timestamp("refresh_token_expires_at").nullable()
    val previousRefreshTokenId = uuid("previous_refresh_token_id").nullable()
    val previousRefreshTokenHash = varchar("previous_refresh_token_hash", 64).nullable()
    val isSignedIn = bool("is_signed_in")
    val lastLogInAt = timestamp("last_log_in_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex(userAuthId, deviceUUID)
    }
}