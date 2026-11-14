package com.bookk.user.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.User
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UserEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var name by UserTable.name
    var lastName by UserTable.lastName
    var email by UserTable.email
    var phone by UserTable.phone
    var nameEncrypted by UserTable.nameEncrypted
    var lastNameEncrypted by UserTable.lastNameEncrypted
    var emailEncrypted by UserTable.emailEncrypted
    var phoneEncrypted by UserTable.phoneEncrypted
    var emailIndex by UserTable.emailIndex
    var phoneIndex by UserTable.phoneIndex
    var createdAt by UserTable.createdAt
    var updatedAt by UserTable.updatedAt

    val isPlaintext: Boolean
        get() = emailEncrypted == null

    fun plaintextDomain(): User {
        return User(
            id = id.value,
            name = name.orEmpty(),
            lastName = lastName.orEmpty(),
            email = email.orEmpty(),
            phone = phone,
        )
    }

    fun store(profile: EncryptedProfile, indexes: ContactIndexes, emailChanged: Boolean, phoneChanged: Boolean) {
        val unindexed = isPlaintext
        nameEncrypted = profile.name
        lastNameEncrypted = profile.lastName
        emailEncrypted = profile.email
        phoneEncrypted = profile.phone
        name = null
        lastName = null
        email = null
        phone = null
        if (unindexed || emailChanged) emailIndex = indexes.email
        if (unindexed || phoneChanged) phoneIndex = indexes.phone
    }

    companion object : DecoratorUuidEntityClass<UserEntity>(UserTable) {
        fun new(id: Uuid, profile: EncryptedProfile, indexes: ContactIndexes, updatedAt: Instant): UserEntity {
            return new(id) {
                store(profile, indexes, emailChanged = true, phoneChanged = true)
                this.updatedAt = updatedAt
            }
        }

        fun findByIdForUpdate(id: Uuid): UserEntity? {
            return find { UserTable.id eq id }.forUpdate().singleOrNull()
        }
    }
}

class EncryptedProfile(val name: ByteArray, val lastName: ByteArray, val email: ByteArray, val phone: ByteArray?)

class ContactIndexes(val email: String, val phone: String?)
