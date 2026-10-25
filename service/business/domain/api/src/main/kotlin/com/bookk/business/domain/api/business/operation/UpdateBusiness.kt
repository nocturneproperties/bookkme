package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface UpdateBusiness {
    suspend operator fun invoke(requestUserId: Uuid, businessUpdateModel: BusinessUpdateModel): Result<Unit>

    sealed interface Error {
        class ActiveDayWithoutWorkHours : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_ACTIVE_DAY_WITHOUT_WORK_HOURS,
            message = "Active day must have at least one work hour"
        ), Error

        class InvalidDayOffRange : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_INVALID_DAY_OFF_RANGE,
            message = "Day off range start date must not be after end date"
        ), Error

        class InvalidCurrency : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_INVALID_CURRENCY,
            message = "Currency code is not a supported ISO 4217 currency"
        ), Error

        class CurrencyLocked : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_CURRENCY_LOCKED,
            message = "Currency cannot be changed once the business has services"
        ), Error
    }
}
