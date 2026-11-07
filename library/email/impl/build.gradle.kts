plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    api(projects.library.email.api)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.data.cache.api)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.json)
    implementation(libs.kotlin.serialization.json)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data.cache.impl))
    testImplementation(libs.ktor.client.mock)
}
