package com.bookk.user.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.user.data.orm.table.LegalDocumentAcceptanceTable
import com.bookk.user.data.orm.table.LegalDocumentVersionTable
import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class LegalDocumentDataSourceImpl : DataSource(), LegalDocumentDataSource {

    override suspend fun getVersionsInEffect(now: Instant): List<LegalDocumentVersion> = dbQuery {
        LegalDocumentVersionTable.selectAll()
            .where { LegalDocumentVersionTable.effectiveAt lessEq now }
            .map { it.toVersion() }
            .groupBy { it.document }
            .map { (_, versions) -> versions.maxBy { it.effectiveAt } }
    }

    override suspend fun getVersion(document: LegalDocument, version: String): LegalDocumentVersion? = dbQuery {
        LegalDocumentVersionTable.selectAll()
            .where { (LegalDocumentVersionTable.document eq document) and (LegalDocumentVersionTable.version eq version) }
            .singleOrNull()
            ?.toVersion()
    }

    override suspend fun getAcceptances(userId: Uuid): List<LegalDocumentAcceptance> = dbQuery {
        LegalDocumentAcceptanceTable.selectAll()
            .where { LegalDocumentAcceptanceTable.userId eq userId }
            .map {
                LegalDocumentAcceptance(
                    document = it[LegalDocumentAcceptanceTable.document],
                    version = it[LegalDocumentAcceptanceTable.version],
                    acceptedAt = it[LegalDocumentAcceptanceTable.acceptedAt],
                )
            }
    }

    override suspend fun recordAcceptance(userId: Uuid, acceptance: LegalDocumentAcceptance) = dbQuery<Unit> {
        LegalDocumentAcceptanceTable.insertIgnore {
            it[this.userId] = userId
            it[document] = acceptance.document
            it[version] = acceptance.version
            it[acceptedAt] = acceptance.acceptedAt
        }
    }

    private fun ResultRow.toVersion() = LegalDocumentVersion(
        document = this[LegalDocumentVersionTable.document],
        version = this[LegalDocumentVersionTable.version],
        url = this[LegalDocumentVersionTable.url],
        effectiveAt = this[LegalDocumentVersionTable.effectiveAt],
    )
}
