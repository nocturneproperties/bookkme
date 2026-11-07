package library.export

interface BuildDataExports {
    suspend operator fun invoke(): Result<Unit>
}
