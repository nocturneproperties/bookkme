package com.bookk

import com.bookk.build_src.convention.applyConvention
import com.bookk.build_src.tools.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class ClientConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.plugins.kotlin.jvm.get().pluginId)
                apply(libs.plugins.kotlin.serialization.get().pluginId)
            }

            extensions.getByType<JavaPluginExtension>().applyConvention()
            extensions.getByType<KotlinJvmProjectExtension>().applyConvention(target)
            target.tasks.withType(Test::class.java) {
                useJUnitPlatform()
            }

            target.dependencies {
                add("implementation", libs.ktor.protobuf)
                add("implementation", libs.ktor.json)
                add("implementation", libs.ktor.logging)
                add("implementation", libs.ktor.client.log)
                add("implementation", libs.ktor.client.resources)
                add("implementation", libs.ktor.client.negotiation)
                add("implementation", platform(libs.koin.bom))
                add("implementation", libs.koin.core)
                add("implementation", libs.ktor.client.core)
                add("implementation", libs.ktor.client.cio)
                add("implementation", libs.kotlin.coroutines)
                add("testImplementation", libs.kotlin.test)
            }
        }
    }
}