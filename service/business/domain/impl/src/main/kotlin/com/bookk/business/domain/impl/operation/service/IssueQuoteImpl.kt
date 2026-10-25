package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceQuote
import com.bookk.business.domain.api.service.operation.IssueServiceQuote
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.business.domain.impl.operation.getServicesExpanded
import com.bookk.core.AppLevelConstants
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.business.client.api.QuoteClaims
import library.signing.TokenIssuer
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class IssueQuoteImpl(
    private val serviceDataSource: ServiceDataSource,
    private val transactionManager: TransactionManager,
    private val tokenIssuer: TokenIssuer
) : IssueServiceQuote {

    override suspend fun invoke(businessId: Uuid, serviceIds: List<Uuid>): Result<ServiceQuote> {
        if (serviceIds.isEmpty()) return Result.failure(IssueServiceQuote.Error.EmptyServiceList())
        return transactionManager.transaction {
            val requestedServices = serviceDataSource.getServicesExpanded(businessId, serviceIds)
                ?: throw IssueServiceQuote.Error.ServiceNotFound()

            val quoteId = Uuid.random()
            val token = buildToken(quoteId, businessId, serviceIds, requestedServices)

            ServiceQuote(id = quoteId, services = requestedServices, token = token)
        }
    }

    private suspend fun buildToken(quoteId: Uuid, businessId: Uuid, serviceIds: List<Uuid>, services: List<Service>): String {
        val total = services.map { it.price }.reduce { acc, price -> acc + price }
        val totalDuration = services.map { it.duration }.reduce { acc, duration -> acc + duration }
        return tokenIssuer.issue(QUOTE_TTL) {
            withJWTId(quoteId.toString())
            .withAudience(AppLevelConstants.domainName)
            .withClaim(QuoteClaims.CLAIM_BUSINESS_ID, businessId.toString())
            .withClaim(QuoteClaims.CLAIM_SERVICES, QuoteClaims.encodeServiceCounts(serviceIds))
            .withClaim(QuoteClaims.CLAIM_TOTAL, total.toString())
            .withClaim(QuoteClaims.CLAIM_DURATION, totalDuration.toString())
        }
    }

    companion object {
        private val QUOTE_TTL = 10.minutes
    }
}
