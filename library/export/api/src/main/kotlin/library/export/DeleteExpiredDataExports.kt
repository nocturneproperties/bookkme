package library.export

interface DeleteExpiredDataExports {
    suspend operator fun invoke(): Result<Unit>
}
