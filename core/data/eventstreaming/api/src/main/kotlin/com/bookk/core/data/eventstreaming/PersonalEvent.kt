package com.bookk.core.data.eventstreaming

import kotlin.uuid.Uuid

interface PersonalEvent : EventStreaming.Event<String> {
    val dataSubject: DataSubject
}

sealed interface DataSubject {
    data class User(val userId: Uuid) : DataSubject

    data class Client(val clientId: Uuid, val userId: Uuid?) : DataSubject
}
