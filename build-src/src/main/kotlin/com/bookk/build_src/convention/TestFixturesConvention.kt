package com.bookk.build_src.convention

import com.bookk.build_src.tools.libs
import org.gradle.api.Project

internal fun Project.applyTestFixturesConvention() {
    pluginManager.withPlugin(libs.plugins.kotlin.fixtures.get().pluginId) {
        configurations.named("testFixturesImplementation") {
            extendsFrom(configurations.getByName("implementation"))
        }
    }
}
