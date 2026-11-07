plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    implementation(projects.core.client)
    api(projects.core.domain)
    api(projects.core.data.eventstreaming.api)
    api(projects.service.user.domain.api)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.mockk)
}
