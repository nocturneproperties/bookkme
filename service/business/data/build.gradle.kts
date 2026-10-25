plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.domain.datasource)
    implementation(projects.service.business.domain.api)
    implementation(projects.service.business.data.source)
    implementation(projects.library.signing.impl)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(libs.testcontainers.junit)
}