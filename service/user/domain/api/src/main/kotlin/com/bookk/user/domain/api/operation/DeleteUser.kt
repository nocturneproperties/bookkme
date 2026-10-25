package com.bookk.user.domain.api.operation

import kotlin.uuid.Uuid

interface DeleteUser {

    suspend operator fun invoke(userId: Uuid): Result<Unit>

}