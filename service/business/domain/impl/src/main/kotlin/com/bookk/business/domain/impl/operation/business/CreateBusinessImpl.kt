package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessCreateRequest
import com.bookk.business.domain.api.business.operation.CreateBusiness
import com.bookk.business.domain.api.user.entity.UserLimits
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.UserLimitsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.user.client.UserClient
import library.validation.CurrencyValidator
import library.validation.NameValidator
import kotlin.uuid.Uuid

internal class CreateBusinessImpl(
    private val businessDataSource: BusinessDataSource,
    private val userLimitsDataSource: UserLimitsDataSource,
    private val businessFounder: BusinessFounder,
    private val userClient: UserClient,
    private val transactionManager: TransactionManager,
) : CreateBusiness {
    override suspend fun invoke(userId: Uuid, request: BusinessCreateRequest, isDemo: Boolean): Result<Business> {
        if (!NameValidator.isValid(request.name)) {
            return Result.failure(CreateBusiness.Error.BusinessValidationError())
        }
        if (!CurrencyValidator.isValid(request.currencyCode)) {
            return Result.failure(CreateBusiness.Error.InvalidCurrency())
        }
        return transactionManager.transaction {
            val limits = userLimitsDataSource.lockForUser(userId)
            val maxBusinesses = if (isDemo) UserLimits.DEMO_MAX_BUSINESSES else limits.maxBusinesses
            if (businessDataSource.countBusinessesOwnedBy(userId) >= maxBusinesses) {
                throw CreateBusiness.Error.OwnedBusinessesLimitReached()
            }
            val owner = userClient.getUserById(userId).getOrThrow()
            businessFounder.found(owner, BusinessPlan(request.name, request.currencyCode, request.timeZone, isDemo))
        }
    }
}
