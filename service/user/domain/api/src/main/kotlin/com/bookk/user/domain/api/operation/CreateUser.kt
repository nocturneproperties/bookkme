package com.bookk.user.domain.api.operation

import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserId

interface CreateUser {

    suspend operator fun invoke(user: User): Result<UserId>

}