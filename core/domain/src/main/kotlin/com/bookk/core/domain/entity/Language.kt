package com.bookk.core.domain.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class Language {
    @ProtoNumber(0) EN,
    @ProtoNumber(1) UK;

    companion object
}

fun Language.Companion.fromAcceptLanguage(header: String?): Language {
    if (header.isNullOrBlank()) return Language.EN
    val primaryTag = header.split(",").first().split(";").first().trim().lowercase()
    return Language.entries.firstOrNull { primaryTag.startsWith(it.name.lowercase()) } ?: Language.EN
}
