plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
    implementation(projects.core.data.eventstreaming.api)
    api(projects.library.export.api)
}
