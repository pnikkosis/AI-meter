package com.kmnexus.codexmeter.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.refresh.CurrentQuotaStatePublisher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Rebuilds every accounts widget from storage whenever any account's state is published. */
class AccountsWidgetUpdater(
    context: Context,
    private val stateLoader: AccountsWidgetStateLoader,
    private val widget: AccountsWidget = AccountsWidget(),
) : CurrentQuotaStatePublisher {
    private val appContext = context.applicationContext

    // Accounts refresh in parallel and each publish rereads them all. One rebuild at a time means
    // the last write always read the newest data, so an older read never wins.
    private val mutex = Mutex()

    override suspend fun publish(state: CurrentQuotaState) = refreshAll()

    suspend fun refreshAll() {
        mutex.withLock {
            val glanceIds = GlanceAppWidgetManager(appContext).getGlanceIds(AccountsWidget::class.java)
            if (glanceIds.isEmpty()) return
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(appContext, glanceId) { preferences ->
                    val configuration = preferences.toAccountsWidgetConfiguration()
                    preferences.writeAccountsWidgetState(stateLoader.loadAccountsWidgetState(configuration))
                }
            }
            widget.updateAll(appContext)
        }
    }
}
