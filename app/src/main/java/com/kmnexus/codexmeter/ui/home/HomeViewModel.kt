package com.kmnexus.codexmeter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.QuotaLevelThresholds
import com.kmnexus.codexmeter.domain.quota.quotaLevelThresholds
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import com.kmnexus.codexmeter.ui.account.AccountRefreshAllUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Every saved account's stored state, in the user's order, marked hidden or not. */
fun interface HomeAccountsLoader {
    suspend fun loadAccounts(): List<HomeAccountEntry>
}

/** One account's stored state; the notification and widget republishers still read it this way. */
fun interface HomeCurrentQuotaStateLoader {
    suspend fun loadCurrentState(): CurrentQuotaState
}

private object DefaultHomeNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}

class HomeViewModel(
    private val accountsLoader: HomeAccountsLoader,
    private val refreshAllUseCase: AccountRefreshAllUseCase,
    private val notificationPreferenceReader: NotificationPreferenceReader = DefaultHomeNotificationPreferenceReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    private var entries: List<HomeAccountEntry> = emptyList()
    private var thresholds = QuotaLevelThresholds()
    private var loaded = false
    private var isRefreshing = false
    private var loadJob: Job? = null

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Shows what is stored. Deliberately does NOT call the providers: opening Home (and every
     * ON_RESUME) must not hit the network; fresh figures come from [refreshAll] and the periodic
     * background refresh.
     */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { reload() }
    }

    /** Asks every connected account's provider for fresh figures, then shows what was stored. */
    fun refreshAll() {
        if (isRefreshing) return
        isRefreshing = true
        publish()
        viewModelScope.launch {
            try {
                try {
                    refreshAllUseCase.refreshAll()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    // Each account records its own failure; the cards say so after the reload.
                }
                reload()
            } finally {
                isRefreshing = false
                publish()
            }
        }
    }

    private suspend fun reload() {
        thresholds = try {
            notificationPreferenceReader.notificationPreferences().quotaLevelThresholds()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            thresholds
        }
        entries = try {
            accountsLoader.loadAccounts()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            entries
        }
        loaded = true
        publish()
    }

    private fun publish() {
        _uiState.value = HomeUiStateMapper(thresholds)
            .map(entries, isRefreshing)
            .copy(isLoading = !loaded)
    }

    companion object {
        fun factory(
            accountsLoader: HomeAccountsLoader,
            refreshAllUseCase: AccountRefreshAllUseCase,
            notificationPreferenceReader: NotificationPreferenceReader = DefaultHomeNotificationPreferenceReader,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(
                        accountsLoader = accountsLoader,
                        refreshAllUseCase = refreshAllUseCase,
                        notificationPreferenceReader = notificationPreferenceReader,
                    ) as T
            }
    }
}
