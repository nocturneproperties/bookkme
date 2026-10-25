plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.client)
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
    implementation(projects.service.user.domain.api)

    testImplementation(testFixtures(projects.core))
}
