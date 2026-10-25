package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientUpdateModel
import com.bookk.core.data.DecoratorUuidEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class ClientEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    val businessId by ClientTable.businessId
    var name by ClientTable.name
    var lastName by ClientTable.lastName
    var phone by ClientTable.phone
    var email by ClientTable.email
    val userId by ClientTable.userId
    var description by ClientTable.description

    companion object : DecoratorUuidEntityClass<ClientEntity>(ClientTable) {
        fun findByIdAndUpdate(businessId: Uuid, model: ClientUpdateModel): ClientEntity? =
            findByIdAndUpdate(model.id) {
                if (it.businessId.value == businessId) {
                    model.name?.let { name -> it.name = name }
                    model.lastName?.let { lastName -> it.lastName = lastName }
                    model.phone?.let { phone -> it.phone = phone }
                    model.email?.let { email -> it.email = email }
                    model.description?.let { description -> it.description = description }
                }
            }?.takeIf { it.businessId.value == businessId }
    }

    fun toDomain(): Client {
        return when (val userId = userId) {
            null -> Client.Detached(
                id = id.value,
                name = name,
                lastName = lastName,
                phone = phone,
                email = email,
                description = description
            )

            else -> Client.Integrated(
                id = id.value,
                name = name,
                lastName = lastName,
                phone = phone,
                email = email,
                userId = userId,
                description = description
            )
        }
    }
}
