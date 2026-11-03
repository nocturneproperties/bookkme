plugins {
    alias(libs.plugins.bookk.data)
}

dependencies {
    api(projects.library.export.api)
    implementation(projects.core.domain)
    implementation(projects.core.domain.datasource)
    implementation(projects.core.data)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.core.data))
    testImplementation(testFixtures(projects.core.domain.datasource))
}
