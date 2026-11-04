plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    implementation(libs.ktor.protobuf)
    implementation(libs.ktor.json)
    implementation(libs.ktor.client.log)
    implementation(libs.ktor.client.negotiation)
    implementation(libs.ktor.client.cio)
    api(projects.core.domain)
    implementation(projects.core)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.ktor.client.mock)
}
