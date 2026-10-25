package com.bookk.core.i18n

import com.bookk.core.domain.entity.Language
import java.util.Locale

fun Language.toLocale(): Locale = when (this) {
    Language.EN -> Locale.ENGLISH
    Language.UK -> Locale.forLanguageTag("uk")
}
