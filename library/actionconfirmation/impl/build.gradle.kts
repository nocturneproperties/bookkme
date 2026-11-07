plugins {
    alias(libs.plugins.bookk.data)
    alias(libs.plugins.kotlin.fixtures)
}

dependencies {
    api(projects.library.actionconfirmation.api)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.data.cache.api)
    api(projects.library.signing.api)

    testFixturesImplementation(testFixtures(projects.core.data.cache.impl))

    testImplementation(testFixtures(projects.core))
}
