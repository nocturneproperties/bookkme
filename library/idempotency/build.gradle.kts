plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    api(libs.ktor.core)
    implementation(libs.kotlin.serialization.json)
}
