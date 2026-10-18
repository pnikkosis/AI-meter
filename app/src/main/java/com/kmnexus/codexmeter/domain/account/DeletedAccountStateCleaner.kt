package com.kmnexus.codexmeter.domain.account

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot

data class AccountListResult(
    val accounts: List<ProviderAccount>,
    val currentAccountId: LocalAccountId?,
    val latestQuotaSnapshots: Map<LocalAccountId, QuotaSnapshot> = emptyMap(),
)

fun interface AccountListUseCase {
    suspend fun loadAccounts(): AccountListResult
}

object NoopAccountListUseCase : AccountListUseCase {
    override suspend fun loadAccounts(): AccountListResult =
        AccountListResult(accounts = emptyList(), currentAccountId = null)
}

/**
 * What happened on the provider's side when an account was disconnected. Local data is deleted in
 * every case; this only says whether the provider was also told to invalidate the sign-in.
 */
enum class RemoteRevocation {
    /** The provider confirmed that the stored token was revoked. */
    Revoked,

    /** The provider offers no revocation the app can call; only local data was deleted. */
    NotSupported,

    /** Revocation was attempted and failed (offline, timeout, error answer). */
    Failed,

    /** There was no stored token to revoke. */
    NothingToRevoke,
}

fun interface AccountDeleteUseCase {
    /** Deletes the account's local data (always) after a best-effort remote revocation. */
    suspend fun deleteAccount(providerId: ProviderId, localAccountId: LocalAccountId): RemoteRevocation
}

object NoopAccountDeleteUseCase : AccountDeleteUseCase {
    override suspend fun deleteAccount(providerId: ProviderId, localAccountId: LocalAccountId) =
        RemoteRevocation.NothingToRevoke
}

fun interface AccountSwitchUseCase {
    suspend fun switchCurrentAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean
}

object NoopAccountSwitchUseCase : AccountSwitchUseCase {
    override suspend fun switchCurrentAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean = false
}

fun interface AccountRenameUseCase {
    suspend fun renameAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        displayName: String,
    ): ProviderAccount?
}

object NoopAccountRenameUseCase : AccountRenameUseCase {
    override suspend fun renameAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        displayName: String,
    ): ProviderAccount? = null
}

fun interface AccountSwitchRefreshRequester {
    suspend fun requestAccountSwitchRefresh(account: ProviderAccount)
}

object NoopAccountSwitchRefreshRequester : AccountSwitchRefreshRequester {
    override suspend fun requestAccountSwitchRefresh(account: ProviderAccount) = Unit
}

/** Republishes one account's stored state after a change that did not refresh it (a rename). */
fun interface AccountStateRepublisher {
    suspend fun republishAccountState(account: ProviderAccount)
}

object NoopAccountStateRepublisher : AccountStateRepublisher {
    override suspend fun republishAccountState(account: ProviderAccount) = Unit
}

/**
 * Redraws the places that list accounts (the status notification and the widgets) after a change
 * that did not refresh quota, such as hiding, reordering or removing an account. It never posts a
 * quota alert or an account error again.
 */
fun interface AccountSurfacesRefresher {
    suspend fun refreshAccountSurfaces()
}

interface DeletedAccountStateCleaner {
    suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup
}

fun interface DeletedAccountStateCleanup {
    suspend fun restore()
}

object NoopDeletedAccountStateCleaner : DeletedAccountStateCleaner {
    override suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup = DeletedAccountStateCleanup {}
}
