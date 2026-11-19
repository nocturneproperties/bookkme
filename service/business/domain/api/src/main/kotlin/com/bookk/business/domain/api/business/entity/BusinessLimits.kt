package com.bookk.business.domain.api.business.entity

data class BusinessLimits(
    val maxEmployees: Int,
    val maxClients: Int,
    val maxServices: Int,
    val maxServiceGroups: Int,
) {
    companion object {
        const val DEFAULT_MAX_EMPLOYEES = 100
        const val DEFAULT_MAX_CLIENTS = 20_000
        const val DEFAULT_MAX_SERVICES = 300
        const val DEFAULT_MAX_SERVICE_GROUPS = 50

        fun stub(
            maxEmployees: Int = DEFAULT_MAX_EMPLOYEES,
            maxClients: Int = DEFAULT_MAX_CLIENTS,
            maxServices: Int = DEFAULT_MAX_SERVICES,
            maxServiceGroups: Int = DEFAULT_MAX_SERVICE_GROUPS,
        ): BusinessLimits {
            return BusinessLimits(maxEmployees, maxClients, maxServices, maxServiceGroups)
        }
    }
}
