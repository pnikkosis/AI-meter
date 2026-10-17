package com.kmnexus.codexmeter.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kmnexus.codexmeter.domain.refresh.RefreshTrigger
import com.kmnexus.codexmeter.refresh.MultiAccountRefreshRunner
import com.kmnexus.codexmeter.refresh.QuotaRefreshDependenciesProvider

/**
 * Widget refresh button. The tap only enqueues work: the network calls run in [WidgetRefreshWorker]
 * under WorkManager, never in the broadcast that delivers the tap. Repeated taps while a refresh is
 * pending are ignored. When the refresh ends, the usual state publisher redraws every widget.
 */
class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetRefreshWorker.enqueue(context)
    }
}

class WidgetRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dependencies = applicationContext as? QuotaRefreshDependenciesProvider ?: return Result.failure()
        MultiAccountRefreshRunner(refreshCoordinator = dependencies.refreshCoordinator).refresh(
            accounts = dependencies.activeQuotaRefreshAccounts(),
            trigger = RefreshTrigger.Widget,
        )
        // Failures are recorded per account and shown on the widget; a tap is not retried later.
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "widget_manual_refresh"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
