plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.domain.datasource)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.domain.datasource))
    testImplementation(libs.testcontainers.junit)
}
