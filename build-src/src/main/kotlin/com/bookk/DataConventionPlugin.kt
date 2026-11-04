package com.bookk

import com.bookk.build_src.convention.applyConvention
import com.bookk.build_src.convention.applyTestFixturesConvention
import com.bookk.build_src.tools.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("unused")
class DataConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.plugins.kotlin.jvm.get().pluginId)
                apply(libs.plugins.kotlin.serialization.get().pluginId)
            }

            extensions.getByType<JavaPluginExtension>().applyConvention()
            applyTestFixturesConvention()
            extensions.getByType<KotlinJvmProjectExtension>().applyConvention(target)
            target.tasks.withType(Test::class.java) {
                useJUnitPlatform()
            }

            target.dependencies {
                add("api", libs.exposed.core)
                add("api", libs.exposed.dao)
                add("api", libs.exposed.jdbc)
                add("implementation", libs.exposed.kotlin.datetime)
                add("api", libs.koin.core)
                add("api", platform(libs.koin.bom))
                add("implementation", libs.kotlin.coroutines)
                add("implementation", libs.kotlin.serialization.protobuf)
                add("testImplementation", libs.kotlin.test)
                add("testImplementation", libs.mockk)
                add("testRuntimeOnly", libs.h2)
                add("testImplementation", libs.kotlin.coroutines.test)
            }
        }
    }
}
