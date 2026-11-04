package com.bookk.user.microservice.route.api

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.server.auth.client.AppPrincipal
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.operation.AcceptLegalDocuments
import com.bookk.user.domain.api.operation.GetLegalDocumentStatus
import com.bookk.user.domain.api.operation.GetLegalDocuments
import com.bookk.user.domain.impl.di.UserScope
import com.bookk.user.microservice.route.UserRouting.Api
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.application

internal fun Route.legalDocuments() {
    /**
     * Get legal documents
     *
     * Summary: Get legal documents
     * Description: The version of each legal document (privacy policy, terms of service) currently in effect, with its link. A document with no published version is left out.
     * Tag: user
     * Response: 200 application/protobuf [com.bookk.user.domain.api.entity.LegalDocumentVersion]+ Legal document versions in effect
     */
    get<Api.User.Legal> {
        val getLegalDocuments by application.injectScoped<GetLegalDocuments>(UserScope)

        call.respondWith(getLegalDocuments())
    }

    authenticate {
        /**
         * Get legal document acceptance
         *
         * Summary: Get legal document acceptance
         * Description: Each legal document version currently in effect, with the time the caller accepted it, or no time when the caller still has to accept it.
         * Tag: user
         * Security: jwt
         * Response: 200 application/protobuf [com.bookk.user.domain.api.entity.LegalDocumentStatus]+ Legal document versions in effect and when the caller accepted them
         */
        get<Api.User.Me.Legal> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val getLegalDocumentStatus by application.injectScoped<GetLegalDocumentStatus>(UserScope)

            call.respondWith(getLegalDocumentStatus(principal.userId))
        }

        /**
         * Accept legal documents
         *
         * Summary: Accept legal documents
         * Description: Records that the caller accepted the sent version of each document. Only a published version already in effect can be accepted. Accepting the same version again changes nothing.
         * Tag: user
         * Security: jwt
         * Body: application/x-protobuf [com.bookk.user.domain.api.entity.AcceptedLegalDocuments] Accepted version of each document, a missing document is left as it is
         * Response: 204 application/x-protobuf Acceptance recorded
         * Response: 404 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] The caller's profile does not exist
         * Response: 422 application/x-protobuf [com.bookk.core.domain.entity.SimpleServerError] Accept legal documents errors<br>LEGAL_DOCUMENT_VERSION_UNKNOWN (100003) Legal document version is not published
         * See: docs/operations/user/accept-legal-documents.md
         */
        post<Api.User.Me.Legal> {
            val principal = requireNotNull(call.principal<AppPrincipal>())
            val body = call.receive<AcceptedLegalDocuments>()
            val acceptLegalDocuments by application.injectScoped<AcceptLegalDocuments>(UserScope)

            call.respondWith(acceptLegalDocuments(principal.userId, body))
        }
    }
}
