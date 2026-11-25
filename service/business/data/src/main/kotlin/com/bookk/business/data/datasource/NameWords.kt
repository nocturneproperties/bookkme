package com.bookk.business.data.datasource

import java.text.Normalizer
import kotlin.math.min

internal object NameWords {
    private const val MIN_PREFIX = 2
    private const val MAX_PREFIX = 4
    private val SEPARATORS = Regex("[\\s-]+")
    private val MARKS = Regex("\\p{M}+")
    private val APOSTROPHES = Regex("['’ʼ`]")

    fun of(value: String): List<String> {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val plain = decomposed.replace(MARKS, "").replace(APOSTROPHES, "").lowercase()
        return plain.split(SEPARATORS).filter { it.isNotEmpty() }
    }

    fun indexedPrefixes(word: String): List<String> {
        val longest = min(MAX_PREFIX, word.codePointLength())
        return (MIN_PREFIX..longest).map { word.codePointPrefix(it) }
    }

    fun searchedPrefix(word: String): String? {
        if (word.codePointLength() < MIN_PREFIX) return null
        return word.codePointPrefix(MAX_PREFIX)
    }

    fun allStartWords(queryWords: List<String>, nameWords: List<String>): Boolean {
        return queryWords.all { queryWord -> nameWords.any { it.startsWith(queryWord) } }
    }

    private fun String.codePointLength(): Int {
        return codePointCount(0, length)
    }

    private fun String.codePointPrefix(codePoints: Int): String {
        return substring(0, offsetByCodePoints(0, min(codePoints, codePointLength())))
    }
}
