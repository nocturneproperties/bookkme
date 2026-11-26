package com.bookk.auth.domain.api.demo.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed interface DemoUpgradeError {
    class NotDemoAccount :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.NOT_A_DEMO_ACCOUNT,
            message = "This account is not a demo account",
        ),
        DemoUpgradeError

    class EmailAlreadyExist :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.EMAIL_EXIST,
            message = "This email already exists",
        ),
        DemoUpgradeError
}
