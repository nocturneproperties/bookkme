package library.validation

import org.joda.money.CurrencyUnit

object CurrencyValidator {
    fun isValid(currencyCode: String): Boolean =
        runCatching { CurrencyUnit.of(currencyCode) }.getOrNull()?.isPseudoCurrency == false
}
