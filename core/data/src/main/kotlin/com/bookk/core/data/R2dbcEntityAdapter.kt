package com.bookk.core.data

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

//Will be handy after R2dbc drivers and exposed libs will be stable, for now it is extremely unstable
abstract class DecoratorUuidEntityClass<out E : UuidEntity>(
    table: IdTable<Uuid>,
    entityType: Class<E>? = null,
    entityCtor: ((EntityID<Uuid>) -> E)? = null
) : UuidEntityClass<E>(table, entityType, entityCtor)

