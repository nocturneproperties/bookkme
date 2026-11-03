package com.bookk.server.auth.client.di

import com.bookk.core.client.internalHttpClient
import com.bookk.server.auth.client.export.ExportAccountData
import com.bookk.server.auth.client.impl.ExportAccountDataClientImpl
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val AUTH_HTTP_CLIENT = "authHttpClient"

fun authClientModule(qualifier: Qualifier, clientTag: String) = module {
    scope(qualifier) {
        scoped(named(AUTH_HTTP_CLIENT)) { internalHttpClient("APPLICATION_AUTH_SERVICE_HOSTNAME", clientTag) }
        scoped<ExportAccountData> { ExportAccountDataClientImpl(get(named(AUTH_HTTP_CLIENT))) }
    }
}
