plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    implementation(projects.core.client)
    api(projects.core.domain)
    api(projects.core.data.eventstreaming.api)
    api(libs.ktor.jwt)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}
