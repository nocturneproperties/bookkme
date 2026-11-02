package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.BookingQuoteError
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.server.business.client.api.QuoteClaims
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import org.joda.money.Money
import kotlin.time.Duration
import kotlin.uuid.Uuid

internal data class QuoteOffer(
    val businessId: String,
    val serviceCounts: Map<Uuid, Int>,
    val total: String,
    val duration: String
)

internal class BookingQuoteVerifier(private val tokenValidatorFactory: TokenValidatorFactory) {

    fun verify(businessId: Uuid, services: List<RequestedService>, offerToken: String): QuoteOffer {
        if (hasInvalidServicesSignature(services)) throw BookingQuoteError.ServicesSignatureMiss()
        val offer = decode(offerToken) ?: throw BookingQuoteError.ServicesSignatureMiss()
        if (!offer.matches(businessId, services)) throw BookingQuoteError.ServicesSignatureMiss()
        return offer
    }

    fun requireUnchanged(offer: QuoteOffer, totalAmount: Money, duration: Duration) {
        if (totalAmount != Money.parse(offer.total)) throw BookingQuoteError.PriceChanged()
        if (duration != Duration.parse(offer.duration)) throw BookingQuoteError.DurationChanged()
    }

    private fun hasInvalidServicesSignature(services: List<RequestedService>): Boolean {
        if (services.any { it.count <= 0 }) return true
        val requestedServiceIds = services.map { it.serviceId }
        return requestedServiceIds.distinct().size != requestedServiceIds.size
    }

    private fun decode(offerToken: String): QuoteOffer? = runCatching {
        val decodedOffer = tokenValidatorFactory.forType(ValidationType.SERVICE_QUOTE).verifier.verify(offerToken)
        QuoteOffer(
            businessId = decodedOffer.getClaim(QuoteClaims.CLAIM_BUSINESS_ID).asString(),
            serviceCounts = QuoteClaims.decodeServiceCounts(decodedOffer.getClaim(QuoteClaims.CLAIM_SERVICES).asList(String::class.java)),
            total = decodedOffer.getClaim(QuoteClaims.CLAIM_TOTAL).asString(),
            duration = decodedOffer.getClaim(QuoteClaims.CLAIM_DURATION).asString()
        )
    }.getOrNull()

    private fun QuoteOffer.matches(businessId: Uuid, services: List<RequestedService>): Boolean =
        services.associate { it.serviceId to it.count } == serviceCounts && businessId.toString() == this.businessId
}
