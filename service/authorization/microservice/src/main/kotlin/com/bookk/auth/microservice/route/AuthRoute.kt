package com.bookk.auth.microservice.route

import com.bookk.auth.microservice.route.api.accountDeletion
import com.bookk.auth.microservice.route.api.confirmAction
import com.bookk.auth.microservice.route.api.deleteAccount
import com.bookk.auth.microservice.route.api.demo
import com.bookk.auth.microservice.route.api.emailChange
import com.bookk.auth.microservice.route.api.healthCheck
import com.bookk.auth.microservice.route.api.internal.exportAccountData
import com.bookk.auth.microservice.route.api.logOut
import com.bookk.auth.microservice.route.api.passkeyOperations
import com.bookk.auth.microservice.route.api.postRefreshToken
import com.bookk.auth.microservice.route.api.registration
import com.bookk.auth.microservice.route.api.signIn
import com.bookk.auth.microservice.route.api.telegramConfirmAction
import com.bookk.auth.microservice.route.api.telegramSignIn
import io.ktor.server.routing.Routing

fun Routing.authRoute() {
    healthCheck()
    passkeyOperations()
    signIn()
    telegramSignIn()
    demo()
    registration()
    emailChange()
    postRefreshToken()
    logOut()
    deleteAccount()
    accountDeletion()
    confirmAction()
    telegramConfirmAction()
    exportAccountData()
}
