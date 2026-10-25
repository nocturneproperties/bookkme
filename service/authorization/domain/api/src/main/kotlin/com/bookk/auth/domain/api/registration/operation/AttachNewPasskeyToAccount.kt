package com.bookk.auth.domain.api.registration.operation

import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import kotlin.uuid.Uuid

interface AttachNewPasskeyToAccount {
    suspend operator fun invoke(authId: Uuid, request: AddPasskeyRequest): Result<Unit>
}