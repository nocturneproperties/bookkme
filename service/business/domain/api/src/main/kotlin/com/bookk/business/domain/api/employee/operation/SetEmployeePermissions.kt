package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

interface SetEmployeePermissions {
    suspend operator fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        employeeId: Uuid,
        grants: Map<BusinessResource, ResourcePermission>
    ): Result<Employee>

    sealed interface Error {
        class OwnerPermissionsImmutable : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_OWNER_PERMISSIONS_IMMUTABLE,
            message = "Business owner always has full permissions"
        ), Error
    }
}
