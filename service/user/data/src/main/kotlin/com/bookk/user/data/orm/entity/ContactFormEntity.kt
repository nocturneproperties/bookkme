package com.bookk.user.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.user.data.orm.table.ContactFormTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class ContactFormEntity(id: EntityID<Uuid>) : UuidEntity(id) {

    var user by UserEntity referencedOn ContactFormTable.userId
    var usageLogs by ContactFormTable.usageLogs
    var text by ContactFormTable.text
    var createdAt by ContactFormTable.createdAt
    var updatedAt by ContactFormTable.updatedAt
    var status by ContactFormTable.status

    companion object : DecoratorUuidEntityClass<ContactFormEntity>(ContactFormTable)
}