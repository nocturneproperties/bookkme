package com.bookk.user.domain.impl.di

import com.bookk.core.data.eventstreaming.EventHandler
import com.bookk.user.domain.api.USER_SCHEMA
import com.bookk.user.domain.api.operation.CreateContactForm
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.api.operation.DeleteUser
import com.bookk.user.domain.api.operation.EditUser
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.api.operation.GetUserById
import com.bookk.user.domain.impl.event.UserEventHandler
import com.bookk.user.domain.impl.operation.CreateContactFormImpl
import com.bookk.user.domain.impl.operation.CreateUserImpl
import com.bookk.user.domain.impl.operation.DeleteUserImpl
import com.bookk.user.domain.impl.operation.EditUserImpl
import com.bookk.user.domain.impl.operation.GetUserByEmailImpl
import com.bookk.user.domain.impl.operation.GetUserByIdImpl
import org.koin.core.module.dsl.scopedOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val UserScope: Qualifier = named(USER_SCHEMA)

fun userDomainModule() = module {
    scope(UserScope) {
        scopedOf(::GetUserByIdImpl) bind GetUserById::class
        scopedOf(::CreateUserImpl) bind CreateUser::class
        scopedOf(::DeleteUserImpl) bind DeleteUser::class
        scopedOf(::EditUserImpl) bind EditUser::class
        scopedOf(::CreateContactFormImpl) bind CreateContactForm::class
        scopedOf(::GetUserByEmailImpl) bind GetUserByEmail::class
        scopedOf(::GetUserByIdImpl) bind GetUserById::class
        scopedOf(::UserEventHandler) bind EventHandler::class
    }
}
