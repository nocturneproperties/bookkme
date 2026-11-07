plugins {
    alias(libs.plugins.bookk.domain.api)
}

dependencies {
    implementation(libs.ktor.http)
    api(libs.kotlin.datetime)
    api(projects.core.domain)
    api(projects.library.money)
    api(projects.library.permissions)
    api(projects.library.schedule)
    api(projects.library.export.api)
}
