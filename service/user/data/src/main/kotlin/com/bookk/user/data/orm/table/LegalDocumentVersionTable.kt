package com.bookk.user.data.orm.table

import com.bookk.user.domain.api.entity.LegalDocument
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object LegalDocumentVersionTable : Table("legal_document_version") {
    val document = enumerationByName<LegalDocument>("document", 32)
    val version = varchar("version", 32)
    val url = varchar("url", 512)
    val effectiveAt = timestamp("effective_at")

    override val primaryKey = PrimaryKey(document, version)
}
