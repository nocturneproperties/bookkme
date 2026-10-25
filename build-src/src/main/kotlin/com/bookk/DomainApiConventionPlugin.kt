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

class DomainApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply(libs.plugins.kotlin.jvm.get().pluginId)
                apply(libs.plugins.kotlin.serialization.get().pluginId)
            }

            extensions.getByType<JavaPluginExtension>().applyConvention()
            extensions.getByType<KotlinJvmProjectExtension>().applyConvention()
            target.tasks.withType(Test::class.java) {
                useJUnitPlatform()
            }

            target.dependencies {
                add("implementation", libs.ktor.protobuf)
                add("implementation", libs.kotlin.serialization.core)
                add("implementation", libs.kotlin.coroutines)
                add("testImplementation", libs.kotlin.test)
                add("testImplementation", libs.mockk)
            }
        }
    }
}