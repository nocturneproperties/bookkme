package library.email.impl

import kotlinx.serialization.Serializable

@Serializable
internal data class HostingerMailRequest(
    val to: List<String>,
    val displayName: String,
    val subject: String,
    val text: String,
    val html: String,
)
