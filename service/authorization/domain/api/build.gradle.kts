plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(libs.ktor.http)
    api(projects.core.domain)
    api(projects.service.user.domain.api)
}
