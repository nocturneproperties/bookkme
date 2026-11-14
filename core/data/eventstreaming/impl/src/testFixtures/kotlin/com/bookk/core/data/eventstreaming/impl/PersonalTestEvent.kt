package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.eventstreaming.DataSubject
import com.bookk.core.data.eventstreaming.PersonalEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class PersonalTestEvent(
    @ProtoNumber(1) val userId: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) override val topic: String,
    @ProtoNumber(4) override val idempotencyKey: String = Uuid.random().toString(),
) : PersonalEvent {
    override val dataSubject: DataSubject get() = DataSubject.User(userId)
}
