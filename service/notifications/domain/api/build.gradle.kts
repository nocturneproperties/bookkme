plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(projects.core)
    implementation(projects.core.domain)
}
