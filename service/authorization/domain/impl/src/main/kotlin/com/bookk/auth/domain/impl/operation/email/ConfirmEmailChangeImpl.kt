package com.bookk.auth.domain.impl.operation.email

import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange
import com.bookk.auth.domain.api.email.operation.ConfirmEmailChange.Error.EmailAlreadyExist
import com.bookk.auth.domain.impl.email.EmailPurpose
import com.bookk.core.domain.entity.BusinessError
import com.bookk.server.user.client.UserClient
import com.bookk.user.domain.api.error.UserErrorCodes
import library.email.EmailCodeError
import library.email.EmailCodeRedeemer
import kotlin.uuid.Uuid

internal class ConfirmEmailChangeImpl(
    private val userClient: UserClient,
    private val emailCodeRedeemer: EmailCodeRedeemer,
) : ConfirmEmailChange {

    override suspend fun invoke(
        userId: Uuid,
        verificationId: Uuid,
        code: String,
    ): Result<Unit> = emailCodeRedeemer.redeem(verificationId, code, ownerId = userId) { verified ->
        if (verified.purpose != EmailPurpose.EMAIL_CHANGE) throw EmailCodeError.Invalid()
        userClient.changeEmail(userId, verified.email).getOrElse { failure ->
            throw if (failure.isAddressTaken()) EmailAlreadyExist() else failure
        }
    }

    private fun Throwable.isAddressTaken(): Boolean = this is BusinessError && code == UserErrorCodes.USER_CONTACT_TAKEN
}
