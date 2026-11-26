package com.bookk.user.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.core.data.forShare
import com.bookk.user.data.orm.table.UserTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UserEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var nameEncrypted by UserTable.nameEncrypted
    var lastNameEncrypted by UserTable.lastNameEncrypted
    var emailEncrypted by UserTable.emailEncrypted
    var phoneEncrypted by UserTable.phoneEncrypted
    var telegramUserIdEncrypted by UserTable.telegramUserIdEncrypted
    var telegramUsernameEncrypted by UserTable.telegramUsernameEncrypted
    var emailIndex by UserTable.emailIndex
    var phoneIndex by UserTable.phoneIndex
    var createdAt by UserTable.createdAt
    var updatedAt by UserTable.updatedAt

    val profile: EncryptedProfile
        get() {
            return EncryptedProfile(
                name = nameEncrypted,
                lastName = lastNameEncrypted,
                email = emailEncrypted,
                phone = phoneEncrypted,
                telegramUserId = telegramUserIdEncrypted,
                telegramUsername = telegramUsernameEncrypted,
            )
        }

    companion object : DecoratorUuidEntityClass<UserEntity>(UserTable) {
        fun new(id: Uuid, profile: EncryptedProfile, indexes: ContactIndexes, updatedAt: Instant): UserEntity {
            return new(id) {
                writeProfile(profile, updatedAt)
                emailIndex = indexes.email
                phoneIndex = indexes.phone
            }
        }

        fun findByIdForUpdate(id: Uuid): UserEntity? {
            return find { UserTable.id eq id }.forUpdate().singleOrNull()
        }

        fun findByIdForShare(id: Uuid): UserEntity? {
            return find { UserTable.id eq id }.forShare().singleOrNull()
        }

        fun findByIdAndUpdateProfile(
            id: Uuid,
            profile: EncryptedProfile,
            updatedAt: Instant,
        ): UserEntity? = findByIdAndUpdate(id) {
            it.writeProfile(profile, updatedAt)
        }

        fun findByIdAndUpdateEmail(
            id: Uuid,
            profile: EncryptedProfile,
            emailIndex: String,
            updatedAt: Instant,
        ): UserEntity? = findByIdAndUpdate(id) {
            it.writeProfile(profile, updatedAt)
            it.emailIndex = emailIndex
        }

        fun findByIdAndUpdatePhone(
            id: Uuid,
            profile: EncryptedProfile,
            phoneIndex: String?,
            updatedAt: Instant,
        ): UserEntity? = findByIdAndUpdate(id) {
            it.writeProfile(profile, updatedAt)
            it.phoneIndex = phoneIndex
        }

        private fun UserEntity.writeProfile(profile: EncryptedProfile, updatedAt: Instant) {
            nameEncrypted = profile.name
            lastNameEncrypted = profile.lastName
            emailEncrypted = profile.email
            phoneEncrypted = profile.phone
            telegramUserIdEncrypted = profile.telegramUserId
            telegramUsernameEncrypted = profile.telegramUsername
            this.updatedAt = updatedAt
        }
    }
}

class EncryptedProfile(
    val name: ByteArray,
    val lastName: ByteArray,
    val email: ByteArray,
    val phone: ByteArray?,
    val telegramUserId: ByteArray?,
    val telegramUsername: ByteArray?,
)

class ContactIndexes(val email: String, val phone: String?)
