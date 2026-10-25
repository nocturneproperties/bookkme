package com.bookk.core.service.di

import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import org.koin.core.qualifier.Qualifier
import org.koin.core.scope.Scope
import org.koin.ktor.ext.getKoin
import java.util.concurrent.ConcurrentHashMap

private val scopeAttributeKeys = ConcurrentHashMap<Qualifier, AttributeKey<Scope>>()

private fun attributeKeyFor(qualifier: Qualifier): AttributeKey<Scope> {
    return scopeAttributeKeys.getOrPut(qualifier) { AttributeKey("ServiceScope:${qualifier.value}") }
}

fun Application.installServiceScope(qualifier: Qualifier): Scope {
    val scope = getKoin().createScope(qualifier.value, qualifier)
    attributes.put(attributeKeyFor(qualifier), scope)
    return scope
}

fun Application.serviceScope(qualifier: Qualifier): Scope? {
    return attributes.getOrNull(attributeKeyFor(qualifier))
}

inline fun <reified T : Any> Application.injectScoped(qualifier: Qualifier): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) { serviceScope(qualifier)?.get<T>() ?: getKoin().get<T>() }
}
