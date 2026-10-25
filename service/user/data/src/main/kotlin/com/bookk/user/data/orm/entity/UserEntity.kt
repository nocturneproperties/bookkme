package com.bookk.user.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UserEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var name by UserTable.name
    var lastName by UserTable.lastName
    var email by UserTable.email
    var phone by UserTable.phone
    var createdAt by UserTable.createdAt
    var updatedAt by UserTable.updatedAt

    fun domain(): User = User(
        id = id.value,
        name = name,
        lastName = lastName,
        email = email,
        phone = phone
    )

    companion object : DecoratorUuidEntityClass<UserEntity>(UserTable) {
        fun applyEdit(id: Uuid, model: UserEditModel, updatedAt: Instant): UserEntity? =
            findByIdAndUpdate(id) {
                model.firstName?.let { firstName -> it.name = firstName }
                model.lastName?.let { lastName -> it.lastName = lastName }
                model.email?.let { email -> it.email = email }
                model.phone?.let { phone -> it.phone = phone }
                it.updatedAt = updatedAt
            }
    }
}
