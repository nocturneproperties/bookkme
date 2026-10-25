package com.bookk.auth.data.orm.entity

import com.bookk.auth.data.orm.table.AuthDeviceTable
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class AuthDeviceEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var userAuth by AuthenticationEntity referencedOn AuthDeviceTable.userAuthId
    var deviceUUID by AuthDeviceTable.deviceUUID
    var deviceName by AuthDeviceTable.deviceName
    var language by AuthDeviceTable.language
    var refreshTokenId by AuthDeviceTable.refreshTokenId
    var refreshTokenHash by AuthDeviceTable.refreshTokenHash
    var refreshTokenExpiresAt by AuthDeviceTable.refreshTokenExpiresAt
    var previousRefreshTokenId by AuthDeviceTable.previousRefreshTokenId
    var previousRefreshTokenHash by AuthDeviceTable.previousRefreshTokenHash
    var isSignedIn by AuthDeviceTable.isSignedIn
    var lastLogInAt by AuthDeviceTable.lastLogInAt
    var createdAt by AuthDeviceTable.createdAt
    var updatedAt by AuthDeviceTable.updatedAt

    companion object : DecoratorUuidEntityClass<AuthDeviceEntity>(AuthDeviceTable)
}