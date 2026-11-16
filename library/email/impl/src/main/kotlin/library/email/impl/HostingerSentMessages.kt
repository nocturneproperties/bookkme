package library.email.impl

import kotlinx.serialization.Serializable

@Serializable
internal data class HostingerMessageSearch(val before: String)

@Serializable
internal data class HostingerMessagePage(val data: List<HostingerMessage>)

@Serializable
internal data class HostingerMessage(val uid: Long, val date: String? = null)

@Serializable
internal data class HostingerMessageDeletion(val uids: List<Long>)
