plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    api(projects.library.encryption.api)
    implementation(projects.core.domain)
    implementation(projects.core.data)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(libs.testcontainers.junit)
}
