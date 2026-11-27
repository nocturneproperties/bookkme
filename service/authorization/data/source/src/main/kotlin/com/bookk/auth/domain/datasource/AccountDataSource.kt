package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.authentication.entity.AccountActivity
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.DemoAccount
import com.bookk.auth.domain.api.authentication.entity.DormancyStage
import com.bookk.auth.domain.api.authentication.entity.DormantAccount
import com.bookk.auth.domain.api.authentication.entity.LockedAccount
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AccountDataSource {
    suspend fun createAuthorization(info: Authentication): Authentication
    suspend fun getAuthRecordById(id: Uuid): Authentication?
    suspend fun lockAccount(id: Uuid): Boolean
    suspend fun lockAccountOfUser(userId: Uuid): LockedAccount?
    suspend fun getAuthRecordByUUID(uuid: Uuid): Authentication?
    suspend fun getAuthRecordByUserId(userId: Uuid): Authentication?
    suspend fun getAccountActivity(userId: Uuid): AccountActivity?
    suspend fun deleteAuthorization(authId: Uuid)
    suspend fun recordActivity(authId: Uuid, at: Instant, unlessRecordedSince: Instant)
    suspend fun getDormantAccounts(inactiveSince: Instant): List<DormantAccount>
    suspend fun getDormantAccountForUpdate(userId: Uuid): DormantAccount?
    suspend fun setDormancyStage(userId: Uuid, stage: DormancyStage, warnedAt: Instant?)
    suspend fun getLastActiveAt(userIds: Collection<Uuid>): Map<Uuid, Instant>
    suspend fun countDemoAccountsActiveAt(now: Instant): Long
    suspend fun getDemoAccountsExpiredBefore(before: Instant): List<DemoAccount>
    suspend fun getDemoAccountForUpdate(authId: Uuid): DemoAccount?
    suspend fun endDemo(authId: Uuid)
}
