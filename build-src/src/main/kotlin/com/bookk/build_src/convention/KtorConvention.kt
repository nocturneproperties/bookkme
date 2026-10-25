package com.bookk.build_src.convention

import io.ktor.plugin.features.DockerExtension
import io.ktor.plugin.features.DockerImageRegistry
import io.ktor.plugin.features.FatJarExtension
import io.ktor.plugin.features.KtorExtension
import io.ktor.plugin.features.OpenApiExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.getByType

fun KtorExtension.applyConvention(project: Project) {
    (this as ExtensionAware).extensions.getByType<OpenApiExtension>().apply {
        enabled.set(true)
        codeInferenceEnabled.set(false)
        onlyCommented.set(true)
    }
    (this as ExtensionAware).extensions.getByType<FatJarExtension>().apply {
        allowZip64.set(true)
        archiveFileName.set(
            project.extensions.getByType<BasePluginExtension>().archivesName.map { name -> "$name-all.jar" }
        )
    }
    (this as ExtensionAware).extensions.getByType<DockerExtension>().apply {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set(project.provider { project.group.toString() })
        imageTag.set(project.provider { project.version.toString() })
        externalRegistry.set(
            DockerImageRegistry.externalRegistry(
                project = project.provider { project.group.toString() },
                namespace = project.provider { "nocturneprops" },
                hostname = project.provider { "ghcr.io" },
                username = project.provider {
                    System.getenv("DOCKER_USERNAME").orEmpty().ifBlank {
                        project.property("DOCKER_USERNAME").toString()
                    }
                },
                password = project.provider {
                    System.getenv("DOCKER_PASSWORD").orEmpty().ifBlank {
                        project.property("DOCKER_PASSWORD").toString()
                    }
                }
            )
        )
    }
}