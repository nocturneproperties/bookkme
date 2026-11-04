plugins {
    alias(libs.plugins.bookk.kotlin)
}

dependencies {
    implementation(projects.core)
    api(libs.auth0.jwt)
}
