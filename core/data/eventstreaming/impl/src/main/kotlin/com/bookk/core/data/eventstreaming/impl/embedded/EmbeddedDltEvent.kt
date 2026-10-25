package com.bookk.core.data.eventstreaming.impl.embedded

import com.bookk.core.data.eventstreaming.EventStreaming
import kotlin.uuid.Uuid

data class EmbeddedDltEvent(
    val originalEvent: EventStreaming.Event<String>,
    val attempt: Int,
    override val topic: String,
    override val idempotencyKey: String = Uuid.random().toString()
) : EventStreaming.Event<String>
