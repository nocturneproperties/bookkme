plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.client)
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
    api(projects.service.business.domain.api)
    api(projects.library.schedule)

    testImplementation(testFixtures(projects.core))
}
