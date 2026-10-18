package com.kmnexus.codexmeter.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kmnexus.codexmeter.CodexMeterApp
import com.kmnexus.codexmeter.providers.ProviderRegistry
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chooses which accounts one accounts widget lists; opened when it is placed or reconfigured. */
class AccountsWidgetConfigurationActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(WIDGET_CONFIG_DIM_AMOUNT)

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            CodexMeterTheme {
                AccountsWidgetConfigurationRoute(
                    appWidgetId = appWidgetId,
                    loadState = { loadConfigurationState(appWidgetId) },
                    onCancel = { finish() },
                    onSave = { configuration -> saveConfiguration(appWidgetId, configuration) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private suspend fun loadConfigurationState(appWidgetId: Int): AccountsWidgetConfigurationScreenState =
        withContext(Dispatchers.IO) {
            val app = application as CodexMeterApp
            val glanceId = GlanceAppWidgetManager(this@AccountsWidgetConfigurationActivity).getGlanceIdBy(appWidgetId)
            val preferences = getAppWidgetState<Preferences>(
                context = this@AccountsWidgetConfigurationActivity,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId,
            )
            val accounts = app.homeAccountsLoader.loadAccounts().mapNotNull { entry ->
                val account = entry.state.account ?: return@mapNotNull null
                AccountsWidgetAccountOption(
                    localAccountId = account.localAccountId.value,
                    providerName = ProviderRegistry.displayNameFor(account.providerId),
                    accountName = account.displayName,
                    hiddenOnHome = entry.hidden,
                )
            }
            AccountsWidgetConfigurationScreenState.from(accounts, preferences.toAccountsWidgetConfiguration())
        }

    private fun saveConfiguration(appWidgetId: Int, configuration: AccountsWidgetConfiguration) {
        activityScope.launch {
            val app = application as CodexMeterApp
            val context = this@AccountsWidgetConfigurationActivity
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            val state = withContext(Dispatchers.IO) {
                app.accountsWidgetStateLoader.loadAccountsWidgetState(configuration)
            }
            updateAppWidgetState(context, glanceId) { preferences ->
                preferences.writeAccountsWidgetConfiguration(configuration)
                preferences.writeAccountsWidgetState(state)
            }
            AccountsWidget().update(context, glanceId)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    private companion object {
        const val WIDGET_CONFIG_DIM_AMOUNT = 0.32f
    }
}
