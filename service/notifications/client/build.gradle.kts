plugins {
    alias(libs.plugins.bookk.client)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
}
