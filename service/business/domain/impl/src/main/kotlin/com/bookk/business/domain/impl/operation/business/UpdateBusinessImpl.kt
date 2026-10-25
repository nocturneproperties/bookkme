package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.api.business.operation.UpdateBusiness
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.RowLock
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.server.business.client.api.BusinessDTO
import com.bookk.server.business.client.api.event.BusinessEvent
import library.permissions.PermissionAction
import library.permissions.assert
import library.validation.CurrencyValidator
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class UpdateBusinessImpl(
    private val businessDataSource: BusinessDataSource,
    private val serviceDataSource: ServiceDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : UpdateBusiness {
    override suspend fun invoke(requestUserId: Uuid, businessUpdateModel: BusinessUpdateModel): Result<Unit> =
        transactionManager.transaction {
            val currencyChange = businessUpdateModel.currencyCode?.let { requested ->
                CurrencyChange(requested = requested, stored = lockStoredCurrencyCode(businessUpdateModel.id))
            }
            businessPermissionDataSource.getPermission(requestUserId, businessUpdateModel.id, BusinessResource.BUSINESS)
                .assert(PermissionAction.UPDATE)
            businessUpdateModel.schedule?.let { schedule ->
                if (schedule.days.values.any { it.isActive && it.workingTime.isEmpty() }) {
                    throw UpdateBusiness.Error.ActiveDayWithoutWorkHours()
                }
                if (schedule.dayOffs.any { it.start > it.end }) {
                    throw UpdateBusiness.Error.InvalidDayOffRange()
                }
            }
            currencyChange?.let { requireCurrencyChangeAllowed(businessUpdateModel.id, it) }
            val name = businessUpdateModel.name?.take(Business.MAX_NAME_LENGTH)
            val description = businessUpdateModel.description?.take(Business.MAX_DESCRIPTION_LENGTH)
            val address = businessUpdateModel.address?.take(Business.MAX_ADDRESS_LENGTH)
            val socials = businessUpdateModel.socials?.map {
                it.copy(value = it.value?.take(Business.MAX_SOCIAL_LENGTH))
            }
            val updatedModel = businessUpdateModel.copy(
                name = name,
                description = description,
                address = address,
                socials = socials
            )
            val updatedAt = Clock.System.now()
            businessDataSource.updateBusiness(updatedModel, updatedAt).also { business ->
                eventProducer.send(
                    BusinessEvent.Updated(
                        business = BusinessDTO.from(business),
                        updatedAt = updatedAt
                    )
                )
            }
        }

    private suspend fun lockStoredCurrencyCode(businessId: Uuid): String =
        businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE)?.currencyCode ?: throw Error.NotFound()

    private suspend fun requireCurrencyChangeAllowed(businessId: Uuid, change: CurrencyChange) {
        if (change.requested == change.stored) return
        if (!CurrencyValidator.isValid(change.requested)) throw UpdateBusiness.Error.InvalidCurrency()
        val pricesAreDenominatedInStoredCurrency = CurrencyValidator.isValid(change.stored)
        if (pricesAreDenominatedInStoredCurrency && serviceDataSource.hasServices(businessId)) {
            throw UpdateBusiness.Error.CurrencyLocked()
        }
    }
}

private class CurrencyChange(val requested: String, val stored: String)
