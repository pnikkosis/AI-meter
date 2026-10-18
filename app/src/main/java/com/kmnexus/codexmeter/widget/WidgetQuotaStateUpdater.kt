package com.kmnexus.codexmeter.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import com.kmnexus.codexmeter.refresh.CurrentQuotaStatePublisher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WidgetQuotaStateUpdater(
    context: Context,
    private val factory: WidgetQuotaStateFactory = WidgetQuotaStateFactory(),
    private val widget: CodexMeterWidget = CodexMeterWidget(),
    private val notificationPreferenceReader: NotificationPreferenceReader =
        DefaultWidgetNotificationPreferenceReader,
    private val widgetQuotaStateLoader: WidgetQuotaStateLoader? = null,
) : CurrentQuotaStatePublisher {
    private val appContext = context.applicationContext

    // Accounts refresh in parallel; one rebuild at a time means the last write read the newest data.
    private val mutex = Mutex()

    override suspend fun publish(state: CurrentQuotaState) {
        val notificationPreferences = notificationPreferenceReader.notificationPreferences()
        redraw { configuration ->
            widgetQuotaStateLoader?.loadWidgetQuotaState(configuration)
                ?: factory.create(state, notificationPreferences, configuration.selectedWindowIds)
        }
    }

    /** Rebuilds every widget from storage, for changes that did not come with a new state. */
    suspend fun refreshAll() {
        val loader = widgetQuotaStateLoader ?: return
        redraw { configuration -> loader.loadWidgetQuotaState(configuration) }
    }

    private suspend fun redraw(stateFor: suspend (WidgetQuotaConfiguration) -> WidgetQuotaState) {
        mutex.withLock {
            val glanceIds = GlanceAppWidgetManager(appContext).getGlanceIds(CodexMeterWidget::class.java)
            if (glanceIds.isEmpty()) return
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(appContext, glanceId) { preferences ->
                    preferences.writeWidgetQuotaState(stateFor(preferences.toWidgetQuotaConfiguration()))
                }
            }
            widget.updateAll(appContext)
        }
    }
}

private object DefaultWidgetNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}
