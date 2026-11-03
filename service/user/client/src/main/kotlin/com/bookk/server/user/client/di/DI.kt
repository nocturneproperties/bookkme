package com.bookk.server.user.client.di

import com.bookk.core.client.internalHttpClient
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.impl.UserClientImpl
import com.bookk.server.user.client.impl.operation.CreateUserClientImpl
import com.bookk.server.user.client.impl.operation.DeleteUserClientImpl
import com.bookk.server.user.client.impl.operation.GetUserByEmailClientImpl
import com.bookk.server.user.client.impl.operation.GetUserByIdClientImpl
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.api.operation.DeleteUser
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.api.operation.GetUserById
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module

fun userClientModule(qualifier: Qualifier, clientTag: String) = module {
    scope(qualifier) {
        scoped { internalHttpClient("APPLICATION_USER_SERVICE_HOSTNAME", clientTag) }
        scoped<GetUserById> { GetUserByIdClientImpl(get()) }
        scoped<CreateUser> { CreateUserClientImpl(get()) }
        scoped<DeleteUser> { DeleteUserClientImpl(get()) }
        scoped<GetUserByEmail> { GetUserByEmailClientImpl(get()) }
        scoped<UserClient> { UserClientImpl(get(), get(), get(), get()) }
    }
}
