plugins {
    alias(libs.plugins.bookk.kotlin)
}

dependencies {
    api(projects.core.domain)
    api(projects.service.notifications.domain.api)
}
