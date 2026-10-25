package com.bookk.library.serializer

import org.joda.money.format.MoneyFormatterBuilder

val moneyFormatter = MoneyFormatterBuilder()
    .appendCurrencySymbolLocalized()
    .appendAmount()
    .toFormatter()