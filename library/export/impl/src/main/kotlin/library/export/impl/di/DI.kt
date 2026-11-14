package library.export.impl.di

import library.export.BuildDataExports
import library.export.DataExports
import library.export.DeleteExpiredDataExports
import library.export.impl.BuildDataExportsImpl
import library.export.impl.DataExportDataSource
import library.export.impl.DataExportsImpl
import library.export.impl.DeleteExpiredDataExportsImpl
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.module
import kotlin.time.Clock

fun dataExportModule(qualifier: Qualifier) = module {
    scope(qualifier) {
        scoped { DataExportDataSource() }
        scoped<DataExports> { DataExportsImpl(get(), get(), Clock.System) }
        scoped<BuildDataExports> { BuildDataExportsImpl(get(), get(), get(), get(), Clock.System) }
        scoped<DeleteExpiredDataExports> { DeleteExpiredDataExportsImpl(get(), get(), get(), Clock.System) }
    }
}
