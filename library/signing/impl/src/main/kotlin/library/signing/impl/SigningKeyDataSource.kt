package library.signing.impl

import com.bookk.core.AppLevelConstants
import com.bookk.core.data.DataSource
import library.signing.SigningKey
import library.signing.SigningKeyStatus
import library.signing.impl.key.SigningKeyCipher
import library.signing.impl.orm.entity.SigningKeyEntity
import library.signing.impl.orm.table.SigningKeyTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SigningKeyDataSource : DataSource() {

    suspend fun getActiveKey(): SigningKey? = dbQuery {
        SigningKeyEntity.find { SigningKeyTable.status eq SigningKeyStatus.ACTIVE }
            .map { it.toDomain() }
            .singleOrNull()
    }

    suspend fun getVerificationKeys(): List<SigningKey> = dbQuery {
        SigningKeyTable
            .selectAll()
            .where {
                SigningKeyTable.status.eq(SigningKeyStatus.ACTIVE)
                    .or(SigningKeyTable.status.eq(SigningKeyStatus.RETIRING))
            }
            .orderBy(SigningKeyTable.createdAt, SortOrder.DESC)
            .map { SigningKeyEntity.wrapRow(it).toDomain() }
            .toList()
    }

    suspend fun insertKey(publicKeyPem: String, privateKeyPem: String): SigningKey = dbQuery {
        SigningKeyEntity.new {
            publicKey = publicKeyPem
            privateKey = SigningKeyCipher.encrypt(privateKeyPem, AppLevelConstants.signingKeyEncryptionKey)
            status = SigningKeyStatus.ACTIVE
            createdAt = Clock.System.now()
        }.toDomain()
    }

    suspend fun updateStatus(id: Uuid, status: SigningKeyStatus, retiredAt: Instant?) = dbQuery<Unit> {
        SigningKeyEntity.findById(id)?.apply {
            this.status = status
            this.retiredAt = retiredAt
        }
    }

    suspend fun deleteRetiredBefore(threshold: Instant) = dbQuery<Unit> {
        SigningKeyTable.deleteWhere {
            status.eq(SigningKeyStatus.RETIRING).and(retiredAt.less(threshold))
        }
    }

    private fun SigningKeyEntity.toDomain(): SigningKey = SigningKey(
        id = id.value,
        publicKeyPem = publicKey,
        privateKeyPem = SigningKeyCipher.decrypt(privateKey, AppLevelConstants.signingKeyEncryptionKey),
        status = status,
        createdAt = createdAt,
        retiredAt = retiredAt
    )
}
