package com.bookk.appointments.domain.impl.operation

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.bookk.appointments.domain.api.entity.BookingQuoteError
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.QuoteClaims
import io.mockk.every
import io.mockk.mockk
import library.signing.TokenValidator
import library.signing.TokenValidatorFactory
import library.signing.ValidationType
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

internal class BookingQuoteVerifierTest {

    private class SutFixture {
        val issuerKey: Algorithm = Algorithm.HMAC256("business-service-key")
        val tokenValidatorFactory = mockk<TokenValidatorFactory>()
        val sut = BookingQuoteVerifier(tokenValidatorFactory)

        val businessId = Uuid.random()
        val haircut = Uuid.random()
        val shave = Uuid.random()

        init {
            val validator = mockk<TokenValidator>()
            every { tokenValidatorFactory.forType(ValidationType.SERVICE_QUOTE) } returns validator
            every { validator.verifier } returns JWT.require(issuerKey).withAudience(AUDIENCE).build()
        }

        fun quote(
            serviceIds: List<Uuid> = listOf(haircut, haircut, shave),
            businessIdClaim: String = businessId.toString(),
            signingKey: Algorithm = issuerKey,
            expiresIn: kotlin.time.Duration = 10.minutes,
            withoutClaim: String? = null,
        ): String {
            val claims = mapOf<String, JWTCreator.Builder.() -> JWTCreator.Builder>(
                QuoteClaims.CLAIM_BUSINESS_ID to { withClaim(QuoteClaims.CLAIM_BUSINESS_ID, businessIdClaim) },
                QuoteClaims.CLAIM_SERVICES to {
                    withClaim(QuoteClaims.CLAIM_SERVICES, QuoteClaims.encodeServiceCounts(serviceIds))
                },
                QuoteClaims.CLAIM_TOTAL to { withClaim(QuoteClaims.CLAIM_TOTAL, "USD 45.00") },
                QuoteClaims.CLAIM_DURATION to { withClaim(QuoteClaims.CLAIM_DURATION, "1h 30m") },
            )
            val expiresAt = (Clock.System.now() + expiresIn).toJavaInstant()
            return claims.filterKeys { it != withoutClaim }.values
                .fold(JWT.create().withAudience(AUDIENCE).withExpiresAt(expiresAt)) { token, claim -> token.claim() }
                .sign(signingKey)
        }

        fun requested(vararg services: Pair<Uuid, Int>): List<RequestedService> {
            return services.map { (id, count) -> RequestedService(serviceId = id, count = count) }
        }

        private companion object {
            const val AUDIENCE = "service-quote"
        }
    }

    @Test
    fun `should accept the quoted services in any order and give the quoted total and duration`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.shave to 1, fixture.haircut to 2)

        whenn()
        val offer = fixture.sut.verify(fixture.businessId, services, fixture.quote())

        then()
        assertEquals(mapOf(fixture.haircut to 2, fixture.shave to 1), offer.serviceCounts)
        assertEquals("USD 45.00", offer.total)
        assertEquals("1h 30m", offer.duration)
    }

    @Test
    fun `should reject an expired quote`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 2, fixture.shave to 1)
        val expired = fixture.quote(expiresIn = (-1).minutes)

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, expired) }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should reject a quote not signed by the business service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 2, fixture.shave to 1)
        val forged = fixture.quote(signingKey = Algorithm.HMAC256("forged-key"))

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, forged) }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should reject a quote that is not a token at all`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 2, fixture.shave to 1)

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, "not-a-token") }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should reject a signed quote missing its total`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 2, fixture.shave to 1)
        val withoutTotal = fixture.quote(withoutClaim = QuoteClaims.CLAIM_TOTAL)

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, withoutTotal) }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should reject a quote issued for another business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 2, fixture.shave to 1)
        val otherBusinessQuote = fixture.quote(businessIdClaim = Uuid.random().toString())

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, otherBusinessQuote) }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should reject a booking asking for more of a service than was quoted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 3, fixture.shave to 1)

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, fixture.quote()) }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should reject a booking adding a service that was not quoted`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 2, fixture.shave to 1, Uuid.random() to 1)

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, fixture.quote()) }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should reject a booking leaving out a quoted service`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val services = fixture.requested(fixture.haircut to 2)

        whenn()
        val verify = { fixture.sut.verify(fixture.businessId, services, fixture.quote()) }

        then()
        assertThrows(BookingQuoteError.ServicesSignatureMiss::class.java) { verify() }
    }

    @Test
    fun `should accept an unchanged total written with another scale`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val offer = fixture.sut.verify(
            fixture.businessId,
            fixture.requested(fixture.haircut to 2, fixture.shave to 1),
            fixture.quote(),
        )

        whenn()
        val verify = { fixture.sut.verifyOfferUnchanged(offer, Money.parse("USD 45"), 90.minutes) }

        then()
        verify()
    }

    @Test
    fun `should report a changed price`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val offer = fixture.sut.verify(
            fixture.businessId,
            fixture.requested(fixture.haircut to 2, fixture.shave to 1),
            fixture.quote(),
        )

        whenn()
        val verify = { fixture.sut.verifyOfferUnchanged(offer, Money.parse("USD 45.01"), 90.minutes) }

        then()
        assertThrows(BookingQuoteError.PriceChanged::class.java) { verify() }
    }

    @Test
    fun `should report the same amount in another currency as a changed price`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val offer = fixture.sut.verify(
            fixture.businessId,
            fixture.requested(fixture.haircut to 2, fixture.shave to 1),
            fixture.quote(),
        )

        whenn()
        val verify = { fixture.sut.verifyOfferUnchanged(offer, Money.parse("EUR 45.00"), 90.minutes) }

        then()
        assertThrows(BookingQuoteError.PriceChanged::class.java) { verify() }
    }

    @Test
    fun `should report a changed duration`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val offer = fixture.sut.verify(
            fixture.businessId,
            fixture.requested(fixture.haircut to 2, fixture.shave to 1),
            fixture.quote(),
        )

        whenn()
        val verify = { fixture.sut.verifyOfferUnchanged(offer, Money.parse("USD 45.00"), 95.minutes) }

        then()
        assertThrows(BookingQuoteError.DurationChanged::class.java) { verify() }
    }
}
