package com.bookk.auth.microservice.route.api

import com.bookk.auth.domain.api.demo.entity.DemoStartRequest
import com.bookk.auth.domain.api.demo.operation.StartDemo
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

internal fun Route.demo() {
    /**
     * Start a demo account
     *
     * Summary: Start a demo account
     * Description: Create a 14-day demo account without signing up and sign the device in. The account gets the name Demo and a placeholder email on demoundeliverable.org, which is never emailed, and a sample business in timeZone (an IANA id, UTC when unknown) is created for it shortly after. The device's refresh token is the account's only credential until the upgrade, so signing out or reinstalling loses the demo. acceptedDocuments must name the version in effect of every published legal document (GET /api/user/legal). Device language comes from Accept-Language.
     * Tag: auth
     * Body: application/x-protobuf [com.bookk.auth.domain.api.demo.entity.DemoStartRequest] Device, accepted legal documents and the time zone of the sample business
     * Response: 200 application/x-protobuf [com.bookk.auth.domain.api.token.entity.AuthTokens] Authentication tokens of the demo account
     * Response: 400 application/x-protobuf The body is missing deviceInfo or acceptedDocuments
     * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Demo start errors<br>ACCOUNT_CREATION_FAILED (5) Error during account creation, try again later<br>LEGAL_DOCUMENT_VERSION_UNKNOWN (100003) Legal document version is not published<br>LEGAL_DOCUMENTS_NOT_ACCEPTED (100004) Every legal document in effect must be accepted
     * Response: 429 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] DEMO_UNAVAILABLE (28) No demo account can be started right now, try again later
     * See: docs/operations/authorization/demo-start.md
     */
    post<Api.Auth.Demo> {
        val request = call.receive<DemoStartRequest>()
        val language = Language.fromAcceptLanguage(call.request.headers[HttpHeaders.AcceptLanguage])
        val startDemo by application.injectScoped<StartDemo>(AuthScope)
        call.respondWith(startDemo(request, language))
    }
}
