package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.entity.Error
import kotlin.uuid.Uuid

internal suspend fun BusinessDataSource.requireOwner(userId: Uuid, businessId: Uuid) {
    if (!isOwner(userId, businessId)) throw Error.OperationNotAllowed()
}
