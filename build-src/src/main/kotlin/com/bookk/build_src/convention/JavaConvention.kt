package com.bookk.build_src.convention

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension

fun JavaPluginExtension.applyConvention() {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
