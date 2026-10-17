package com.kmnexus.codexmeter.domain.settings

data class QuotaHistoryClearResult(
    val deletedQuotaSnapshots: Int,
    val deletedRefreshAttempts: Int,
)

interface QuotaHistoryClearUseCase {
    suspend fun clearAllHistory(): QuotaHistoryClearResult
}

object NoopQuotaHistoryClearUseCase : QuotaHistoryClearUseCase {
    override suspend fun clearAllHistory(): QuotaHistoryClearResult =
        QuotaHistoryClearResult(deletedQuotaSnapshots = 0, deletedRefreshAttempts = 0)
}
