package library.email.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PostmarkEmail(
    @SerialName("From") val from: String,
    @SerialName("To") val to: String,
    @SerialName("Subject") val subject: String,
    @SerialName("TextBody") val textBody: String,
    @SerialName("HtmlBody") val htmlBody: String,
    @SerialName("MessageStream") val messageStream: String,
    @SerialName("Tag") val tag: String,
    @SerialName("TrackOpens") val trackOpens: Boolean,
    @SerialName("TrackLinks") val trackLinks: String,
)
