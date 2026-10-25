plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.library.money)
    api(projects.library.schedule)
    api(projects.library.permissions)
    testImplementation(testFixtures(projects.core))
}