package com.bookk.server.business.client.api

import kotlin.uuid.Uuid

object QuoteClaims {
    const val CLAIM_BUSINESS_ID = "business_id"
    const val CLAIM_SERVICES = "services"
    const val CLAIM_TOTAL = "services_total"
    const val CLAIM_DURATION = "services_duration"

    private const val SERVICE_COUNT_SEPARATOR = ":"

    fun encodeServiceCounts(serviceIds: List<Uuid>): List<String> =
        serviceIds.groupingBy { it }.eachCount().map { (id, count) -> "$id$SERVICE_COUNT_SEPARATOR$count" }

    fun decodeServiceCounts(claim: List<String>): Map<Uuid, Int> =
        claim.mapNotNull { entry -> decodeServiceCount(entry) }.toMap()

    private fun decodeServiceCount(entry: String): Pair<Uuid, Int>? {
        val separatorIndex = entry.lastIndexOf(SERVICE_COUNT_SEPARATOR)
        if (separatorIndex < 0) return null
        val id = runCatching { Uuid.parse(entry.substring(0, separatorIndex)) }.getOrNull() ?: return null
        val count = entry.substring(separatorIndex + 1).toIntOrNull() ?: return null
        return id to count
    }
}
