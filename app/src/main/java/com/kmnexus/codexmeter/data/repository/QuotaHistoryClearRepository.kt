package com.kmnexus.codexmeter.data.repository

import androidx.room.withTransaction
import com.kmnexus.codexmeter.data.local.db.CodexMeterDatabase
import com.kmnexus.codexmeter.domain.settings.QuotaHistoryClearResult
import com.kmnexus.codexmeter.domain.settings.QuotaHistoryClearUseCase

class QuotaHistoryClearRepository(
    private val database: CodexMeterDatabase,
) : QuotaHistoryClearUseCase {
    override suspend fun clearAllHistory(): QuotaHistoryClearResult =
        database.withTransaction {
            val deletedSnapshots = database.quotaSnapshotDao().deleteAll()
            val deletedAttempts = database.refreshAttemptDao().deleteAll()
            database.alertStateDao().deleteAll()
            QuotaHistoryClearResult(
                deletedQuotaSnapshots = deletedSnapshots,
                deletedRefreshAttempts = deletedAttempts,
            )
        }
}
