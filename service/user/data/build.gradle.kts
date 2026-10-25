plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data.cache.api)
    implementation(projects.service.user.domain.api)
    implementation(projects.service.user.data.source)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.data.cache.impl))
}