package com.bookk.build_src.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private val stdlibOptInMarkers = listOf(
    "kotlin.uuid.ExperimentalUuidApi",
    "kotlin.time.ExperimentalTime",
)

private val libraryOptInMarkers = mapOf(
    "org.jetbrains.kotlinx:kotlinx-serialization-core" to "kotlinx.serialization.ExperimentalSerializationApi",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core" to "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "io.ktor:ktor-io" to "io.ktor.utils.io.ExperimentalKtorApi",
    "io.lettuce:lettuce-core" to "io.lettuce.core.ExperimentalLettuceCoroutinesApi",
)

internal fun KotlinJvmProjectExtension.applyConvention(project: Project) {
    jvmToolchain(21)
    target.compilations.configureEach {
        val compileClasspath = project.configurations.named(compileDependencyConfigurationName)
        val dependencyGraph = compileClasspath.flatMap { it.incoming.resolutionResult.rootComponent }
        compileTaskProvider.configure {
            compilerOptions.optIn.addAll(stdlibOptInMarkers)
            compilerOptions.optIn.addAll(dependencyGraph.map(::optInMarkersResolvableFrom))
        }
    }
}

private fun optInMarkersResolvableFrom(root: ResolvedComponentResult): List<String> {
    val modules = modulesReachableFrom(root)
        .map { "${it.group}:${it.module.removeSuffix("-jvm")}" }
        .toSet()
    return libraryOptInMarkers.filterKeys(modules::contains).values.toList()
}

private fun modulesReachableFrom(root: ResolvedComponentResult): Set<ModuleComponentIdentifier> {
    val visited = mutableSetOf(root)
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        pending.removeFirst().dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .map { it.selected }
            .filter(visited::add)
            .forEach(pending::addLast)
    }
    return visited.mapNotNull { it.id as? ModuleComponentIdentifier }.toSet()
}
