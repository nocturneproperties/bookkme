package library.outbox.di

import com.bookk.core.data.eventstreaming.ExhaustedEventDataSource
import library.outbox.datasource.ExhaustedEventRowDataSourceImpl
import library.outbox.datasource.SealingExhaustedEventDataSource
import org.koin.dsl.ScopeDSL

fun ScopeDSL.exhaustedEventDataSource() {
    scoped<ExhaustedEventDataSource> { SealingExhaustedEventDataSource(ExhaustedEventRowDataSourceImpl(), get()) }
}
