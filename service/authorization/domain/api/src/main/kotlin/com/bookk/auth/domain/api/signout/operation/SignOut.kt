package com.bookk.auth.domain.api.signout.operation

import kotlin.uuid.Uuid

interface SignOut {

    suspend operator fun invoke(deviceId: Uuid): Result<Unit>

}