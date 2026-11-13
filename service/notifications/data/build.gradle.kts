plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.library.outbox)
    implementation(projects.core.domain.datasource)
    api(projects.service.notifications.domain.api)
    implementation(projects.service.notifications.data.source)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.library.encryption.impl))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(libs.testcontainers.junit)
}
