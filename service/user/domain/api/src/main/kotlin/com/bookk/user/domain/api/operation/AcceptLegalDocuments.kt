package com.bookk.user.domain.api.operation

import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import kotlin.uuid.Uuid

interface AcceptLegalDocuments {

    suspend operator fun invoke(userId: Uuid, documents: AcceptedLegalDocuments): Result<Unit>
}
