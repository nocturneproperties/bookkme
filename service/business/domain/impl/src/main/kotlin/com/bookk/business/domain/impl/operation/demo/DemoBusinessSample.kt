package com.bookk.business.domain.impl.operation.demo

import com.bookk.business.domain.api.client.entity.Client
import com.bookk.core.domain.entity.Language
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal object DemoBusinessSample {
    const val BUSINESS_NAME = "Sample Studio"

    val groups: List<SampleGroup> = listOf(
        SampleGroup(
            name = "Hair",
            services = listOf(
                SampleService("Haircut", 45.minutes, priceInEuro = 30),
                SampleService("Beard trim", 30.minutes, priceInEuro = 20),
            ),
        ),
        SampleGroup(
            name = "Nails",
            services = listOf(
                SampleService("Manicure", 60.minutes, priceInEuro = 35),
                SampleService("Pedicure", 60.minutes, priceInEuro = 40),
            ),
        ),
    )

    private val clientNames = listOf(
        "Alex" to "Example",
        "Bella" to "Sample",
        "Chris" to "Demo",
        "Dana" to "Placeholder",
        "Evan" to "Testperson",
        "Fiona" to "Fictional",
        "Gabe" to "Pretend",
        "Hana" to "Madeup",
        "Ivan" to "Imaginary",
        "Jules" to "Mockup",
    )

    private val currencies = mapOf(
        Language.EN to SampleCurrency(CurrencyUnit.EUR, perEuro = 1),
        Language.PL to SampleCurrency(CurrencyUnit.of("PLN"), perEuro = 4),
        Language.UK to SampleCurrency(CurrencyUnit.of("UAH"), perEuro = 40),
    )

    fun currencyFor(language: Language): SampleCurrency {
        return currencies.getValue(language)
    }

    fun clients(): List<Client.Detached> {
        return clientNames.map { (name, lastName) ->
            Client.Detached(
                id = Uuid.random(),
                name = name,
                lastName = lastName,
                phone = null,
                email = "${name.lowercase()}.${lastName.lowercase()}@example.com",
            )
        }
    }
}

internal class SampleGroup(val name: String, val services: List<SampleService>)

internal class SampleService(val name: String, val duration: Duration, val priceInEuro: Int)

internal class SampleCurrency(val unit: CurrencyUnit, private val perEuro: Int) {
    fun priceOf(service: SampleService): Money {
        return Money.of(unit, (service.priceInEuro * perEuro).toBigDecimal())
    }
}
