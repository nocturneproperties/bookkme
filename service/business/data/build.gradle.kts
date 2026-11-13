plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.library.outbox)
    implementation(projects.core.domain.datasource)
    api(projects.service.business.domain.api)
    implementation(projects.service.business.data.source)
    implementation(projects.library.export.impl)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.library.encryption.impl))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.data.cache.impl))
    testImplementation(libs.testcontainers.junit)
}
