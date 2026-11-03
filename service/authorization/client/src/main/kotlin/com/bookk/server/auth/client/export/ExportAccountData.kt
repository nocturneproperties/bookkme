package com.bookk.server.auth.client.export

import kotlin.uuid.Uuid

interface ExportAccountData {
    suspend operator fun invoke(userId: Uuid): Result<AccountDataExport>
}
