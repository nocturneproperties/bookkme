plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    api(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.data.cache.api)
    implementation(projects.core.domain.datasource)
    api(projects.service.appointments.domain.api)
    implementation(projects.service.appointments.data.source)
    implementation(libs.joda.money)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.library.encryption.impl))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.data.cache.impl))
    testImplementation(libs.testcontainers.junit)
}
