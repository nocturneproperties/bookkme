package com.bookk.business.domain.api.business.entity

import kotlin.uuid.Uuid

data class BusinessViewer(
    val permissions: BusinessPermissions,
    val employeeId: Uuid?,
    val clientId: Uuid?
) {
    companion object {
        val NONE = BusinessViewer(permissions = BusinessPermissions.NONE, employeeId = null, clientId = null)

        fun stub(
            permissions: BusinessPermissions = BusinessPermissions.NONE,
            employeeId: Uuid? = null,
            clientId: Uuid? = null
        ) = BusinessViewer(
            permissions = permissions,
            employeeId = employeeId,
            clientId = clientId
        )
    }
}
