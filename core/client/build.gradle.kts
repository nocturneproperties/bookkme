plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.ktor.client.mock)
}
