package com.bookk.build_src.convention

import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal fun KotlinJvmProjectExtension.applyConvention() {
    jvmToolchain(21)
    compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
    compilerOptions.optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    compilerOptions.optIn.add("io.ktor.utils.io.ExperimentalKtorApi")
}