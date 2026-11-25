package com.bookk.appointments.domain.api.entity

import kotlin.time.Instant

data class StartBounds(val from: Instant? = null, val until: Instant? = null) {
    companion object {
        val OPEN = StartBounds()
    }
}
