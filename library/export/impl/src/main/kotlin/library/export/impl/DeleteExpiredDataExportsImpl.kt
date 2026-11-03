package library.export.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.export.DeleteExpiredDataExports
import kotlin.time.Clock

internal class DeleteExpiredDataExportsImpl(
    private val transactionManager: TransactionManager,
    private val dataSource: DataExportDataSource,
    private val clock: Clock,
) : DeleteExpiredDataExports {

    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        dataSource.deleteExpired(clock.now())
    }
}
