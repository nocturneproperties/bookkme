package com.bookk.build_src.tools

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.kotlin.dsl.getByType
import java.util.Properties

fun Project.includeLocalProperties(fileName: String) {
    runCatching {
        val propertiesFile = project.rootProject.file(fileName)
        val properties = Properties()
        properties.load(propertiesFile.inputStream())
        with(extensions.getByType<ExtraPropertiesExtension>()) {
            properties.entries.forEach {
                set(it.key.toString(), it.value)
            }
        }
    }
}

val Project.nativeClassifier: String?
    get() {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> "windows-x86_64"
            osName.contains("linux") -> "linux-x86_64"
            osName.contains("mac") -> "osx-x86_64"
            else -> null
        }
    }

val Project.libs: LibrariesForLibs
    get() = (this as ExtensionAware).extensions.getByName("libs") as LibrariesForLibs
