package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.telegram.entity.TelegramSignInRequest
import com.bookk.auth.domain.api.telegram.operation.SignInWithTelegram
import com.bookk.auth.domain.impl.di.AuthScope
import com.bookk.auth.microservice.route.AuthRouting.Api
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.fromAcceptLanguage
import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.telegramSignIn() {
    /**
     * Sign in with Telegram
     *
     * Summary: Sign in with Telegram
     * Description: Sign in from the Telegram Mini App with its raw Telegram.WebApp.initData, checked against Telegram's Ed25519 signature for this server's bot and accepted for one hour after auth_date. A Telegram user seen for the first time gets a new account named after their Telegram first and last name, with the placeholder email (Telegram user id)@telegramundeliverable.org, which is never emailed. acceptedDocuments must then name the version in effect of every published legal document (GET /api/user/legal) and is recorded with the new account; an existing account ignores it, so a client can send it only after LEGAL_DOCUMENTS_NOT_ACCEPTED. Device language comes from Accept-Language.
     * Tag: auth
     * Body: application/x-protobuf [com.bookk.auth.domain.api.telegram.entity.TelegramSignInRequest] Mini App init data, device and the accepted legal documents
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.token.entity.AuthTokens] Authentication tokens
     * Response: 400 application/x-protobuf The body is missing initData or deviceInfo
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Telegram sign in errors<br>TELEGRAM_DATA_INVALID (14) Telegram data is not signed by Telegram for this bot<br>TELEGRAM_DATA_EXPIRED (15) Telegram data is too old, reopen the Mini App<br>ACCOUNT_CREATION_FAILED (5) Error during account creation, try again later<br>LEGAL_DOCUMENT_VERSION_UNKNOWN (100003) Legal document version is not published<br>LEGAL_DOCUMENTS_NOT_ACCEPTED (100004) Every legal document in effect must be accepted
     * Response: 503 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] TELEGRAM_SIGN_IN_UNAVAILABLE (16) Telegram sign in is not set up on this server
     * See: docs/operations/authorization/telegram-sign-in.md
     */
    post<Api.Auth.Telegram.SignIn> {
        val request = call.receive<TelegramSignInRequest>()
        val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
        val signInWithTelegram by application.injectScoped<SignInWithTelegram>(AuthScope)
        call.respondWith(signInWithTelegram(request, language))
    }
}
