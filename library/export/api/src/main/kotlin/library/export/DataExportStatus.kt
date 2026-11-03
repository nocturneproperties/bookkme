package library.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class DataExportStatus {
    @ProtoNumber(0)
    NONE,

    @ProtoNumber(1)
    RUNNING,

    @ProtoNumber(2)
    READY,

    @ProtoNumber(3)
    FAILED,
}
