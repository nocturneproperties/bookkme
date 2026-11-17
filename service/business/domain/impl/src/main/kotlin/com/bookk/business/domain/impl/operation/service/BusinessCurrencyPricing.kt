package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.LockedBusiness
import com.bookk.business.domain.datasource.RowLock
import com.bookk.core.domain.entity.Error
import library.validation.CurrencyValidator
import org.joda.money.CurrencyUnit
import org.joda.money.Money
import java.math.RoundingMode
import kotlin.uuid.Uuid

internal suspend fun BusinessDataSource.lockSupportedCurrencyOrNull(businessId: Uuid): CurrencyUnit? {
    val locked = lockBusiness(businessId, RowLock.SHARED) ?: throw Error.NotFound()
    return locked.supportedCurrencyOrNull()
}

internal fun LockedBusiness.supportedCurrencyOrNull(): CurrencyUnit? {
    return if (CurrencyValidator.isValid(currencyCode)) CurrencyUnit.of(currencyCode) else null
}

internal fun Money.inCurrencyOrNull(currency: CurrencyUnit): Money? {
    val fitsCurrencyPrecision = amount.stripTrailingZeros().scale() <= currency.decimalPlaces
    return if (fitsCurrencyPrecision) Money.of(currency, amount, RoundingMode.UNNECESSARY) else null
}
