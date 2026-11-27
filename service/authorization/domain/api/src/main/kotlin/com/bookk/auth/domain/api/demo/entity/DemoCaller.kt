package com.bookk.auth.domain.api.demo.entity

import kotlin.uuid.Uuid

sealed interface DemoCaller {
    class AccessToken(val authId: Uuid, val deviceId: Uuid) : DemoCaller

    class RefreshToken(val token: String) : DemoCaller
}
