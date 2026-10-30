package com.bookk.build_src.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
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
        val resolvedArtifacts = compileClasspath.flatMap { it.incoming.artifacts.resolvedArtifacts }
        compileTaskProvider.configure {
            compilerOptions.optIn.addAll(stdlibOptInMarkers)
            compilerOptions.optIn.addAll(resolvedArtifacts.map(::optInMarkersResolvableFrom))
        }
    }
}

private fun optInMarkersResolvableFrom(artifacts: Set<ResolvedArtifactResult>): List<String> {
    val modules = artifacts
        .mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
        .map { "${it.group}:${it.module.removeSuffix("-jvm")}" }
        .toSet()
    return libraryOptInMarkers.filterKeys(modules::contains).values.toList()
}
