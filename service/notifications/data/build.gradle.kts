plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.data)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.core.data.eventstreaming.data)
    implementation(projects.core.domain.datasource)
    implementation(projects.service.notifications.domain.api)
    implementation(projects.service.notifications.data.source)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
}
