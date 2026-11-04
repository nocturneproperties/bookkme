package com.bookk

import com.bookk.build_src.convention.applyConvention
import com.bookk.build_src.convention.applyTestFixturesConvention
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
            applyTestFixturesConvention()
            extensions.getByType<KotlinJvmProjectExtension>().applyConvention(target)
            extensions.getByType<KtorExtension>().applyConvention(target)
            target.tasks.withType(Test::class.java) {
                useJUnitPlatform()
            }

            target.dependencies {
                add("api", libs.ktor.core)
                add("implementation", libs.ktor.cio)
                add("implementation", libs.ktor.logging)
                add("implementation", libs.ktor.server.negotiation)
                add("implementation", libs.ktor.protobuf)
                add("implementation", libs.ktor.server.resources)
                add("implementation", libs.ktor.json)
                add("implementation", libs.ktor.auth)
                add("implementation", libs.ktor.jwt)
                add("implementation", libs.kotlin.coroutines)
                add("implementation", libs.kotlin.datetime)
                add("api", libs.koin.core)
                add("implementation", libs.koin.ktor)
                add("api", platform(libs.koin.bom))
                add("testImplementation", libs.kotlin.test)
                add("testImplementation", libs.mockk)
                add("testFixturesApi", libs.ktor.client.resources)
                add("testFixturesApi", libs.ktor.client.negotiation)
                add("testFixturesApi", libs.ktor.test)
            }
        }
    }
}
