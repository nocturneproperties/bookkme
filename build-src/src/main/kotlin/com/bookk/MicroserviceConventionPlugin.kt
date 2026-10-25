package com.bookk

import com.bookk.build_src.convention.applyConvention
import com.bookk.build_src.tools.libs
import io.ktor.plugin.features.KtorExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("unused")
class MicroserviceConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.plugins.kotlin.jvm.get().pluginId)
                apply(libs.plugins.ktor.get().pluginId)
                apply(libs.plugins.kotlin.serialization.get().pluginId)
                apply(libs.plugins.kotlin.fixtures.get().pluginId)
            }

            extensions.getByType<JavaPluginExtension>().applyConvention()
            extensions.getByType<KotlinJvmProjectExtension>().applyConvention()
            extensions.getByType<KtorExtension>().applyConvention(target)
            target.tasks.withType(Test::class.java) {
                useJUnitPlatform()
            }

            target.dependencies {
                add("implementation", libs.ktor.core)
                add("implementation", libs.ktor.cio)
                add("implementation", libs.ktor.logging)
                add("implementation", libs.ktor.certificates)
                add("implementation", libs.ktor.server.negotiation)
                add("implementation", libs.ktor.protobuf)
                add("implementation", libs.ktor.server.resources)
                add("implementation", libs.ktor.json)
                add("implementation", libs.ktor.auth)
                add("implementation", libs.ktor.jwt)
                add("implementation", libs.kotlin.coroutines)
                add("implementation", libs.kotlin.datetime)
                add("implementation", libs.koin.core)
                add("implementation", libs.koin.ktor)
                add("implementation", libs.koin.ktor)
                add("implementation", platform(libs.koin.bom))
                add("testImplementation", libs.kotlin.test)
                add("testImplementation", libs.ktor.test)
                add("testImplementation", libs.ktor.client.resources)
                add("testImplementation", libs.ktor.client.negotiation)
                add("testImplementation", libs.mockk)
                add("testFixturesImplementation", libs.ktor.client.resources)
                add("testFixturesImplementation", libs.ktor.client.negotiation)
                add("testFixturesImplementation", libs.ktor.test)
            }
        }
    }
}