package com.bookk.server.auth.client

import kotlin.uuid.Uuid

class AppPrincipal(
    val authId: Uuid,
    val userId: Uuid,
    val deviceId: Uuid
)