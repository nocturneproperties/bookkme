package com.bookk.core

import java.util.Locale.getDefault
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun ByteArray.toUUID(): Uuid = Uuid.fromByteArray(this)

@OptIn(ExperimentalUuidApi::class)
fun String.toUUID(): Uuid = Uuid.parse(this)

fun String.safeCapitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
}

fun ClosedRange<Int>.containedIn(other: ClosedRange<Int>) =
    this.start >= other.start && this.endInclusive <= other.endInclusive