package com.bookk.core.service.test

import com.bookk.core.service.di.installServiceScope
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.ScopeDSL
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

suspend fun ApplicationTestBuilder.startScopedApplication(
    qualifier: Qualifier,
    scopedDefinitions: ScopeDSL.() -> Unit
): Application = startScopedApplication(qualifier to scopedDefinitions)

suspend fun ApplicationTestBuilder.startScopedApplication(
    vararg scopes: Pair<Qualifier, ScopeDSL.() -> Unit>
): Application {
    lateinit var started: Application
    application {
        install(Koin) {
            modules(
                module {
                    scopes.forEach { (qualifier, scopedDefinitions) -> scope(qualifier, scopedDefinitions) }
                }
            )
        }
        scopes.forEach { (qualifier, _) -> installServiceScope(qualifier) }
        started = this
    }
    startApplication()
    return started
}
