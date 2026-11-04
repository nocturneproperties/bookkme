plugins {
    alias(libs.plugins.bookk.kotlin)
}

dependencies {
    api(libs.kotlin.datetime)
    api(projects.service.business.domain.api)
}
