package com.kmnexus.codexmeter.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kmnexus.codexmeter.BuildConfig
import com.kmnexus.codexmeter.data.local.dao.ProviderAccountDao
import com.kmnexus.codexmeter.data.local.dao.QuotaSnapshotDao
import com.kmnexus.codexmeter.data.local.dao.RefreshAttemptDao
import com.kmnexus.codexmeter.data.local.db.CODEXMETER_DB_SCHEMA_VERSION
import com.kmnexus.codexmeter.data.local.entity.ProviderAccountEntity
import com.kmnexus.codexmeter.data.secure.SecureSessionStore
import com.kmnexus.codexmeter.domain.auth.DeviceCodeLoginDiagnosticsReader
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.domain.settings.RetentionPreference
import com.kmnexus.codexmeter.domain.settings.RetentionPreferenceReader
import com.kmnexus.codexmeter.refresh.RefreshWorkScheduler
import com.kmnexus.codexmeter.ui.settings.DiagnosticsAccountSummary
import com.kmnexus.codexmeter.ui.settings.SettingsDiagnosticsInput
import com.kmnexus.codexmeter.ui.settings.SettingsDiagnosticsReader
import com.kmnexus.codexmeter.ui.settings.WorkManagerDiagnostics
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SettingsDiagnosticsRepository(
    private val context: Context,
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val sessionStore: SecureSessionStore,
    private val deviceCodeLoginDiagnosticsReader: DeviceCodeLoginDiagnosticsReader,
    private val workManagerStatusReader: WorkManagerStatusReader = AndroidWorkManagerStatusReader(context),
    private val notificationPermissionReader: NotificationPermissionReader = AndroidNotificationPermissionReader(context),
    private val notificationPreferenceReader: NotificationPreferenceReader,
    private val retentionPreferenceReader: RetentionPreferenceReader,
    private val deviceEnvironmentReader: DeviceEnvironmentReader = AndroidDeviceEnvironmentReader(context),
    private val widgetCountReader: WidgetCountReader = GlanceWidgetCountReader(context),
    private val roomSchemaVersion: Int = CODEXMETER_DB_SCHEMA_VERSION,
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) : SettingsDiagnosticsReader {
    override suspend fun diagnosticsInput(): SettingsDiagnosticsInput {
        val loginDiagnostics = deviceCodeLoginDiagnosticsReader.latestDeviceCodeLoginDiagnostics()

        val now = nowMillisProvider()
        val env = deviceEnvironmentReader.read()
        val wm = workManagerStatusReader.status()
        val prefs = notificationPreferenceReader.notificationPreferences()
        val retention = retentionPreferenceReader.retentionPreference()
        val widgetCount = widgetCountReader.configuredWidgetCount()

        val accountSummaries = providerAccountDao.listAll().map { account -> account.diagnosticsSummary() }

        val accountAlertConfigs = buildDiagnosticsAccountAlertConfigs(prefs.accountQuotaAlertSettings)

        return SettingsDiagnosticsInput(
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            androidSdk = Build.VERSION.SDK_INT.toString(),
            deviceCodeLoginStatus = loginDiagnostics.status,
            deviceCodeLoginAttemptId = loginDiagnostics.attemptId?.let { "sha256:${it.diagnosticsSafeHash()}" },
            deviceCodeLoginSafeErrorCode = loginDiagnostics.safeErrorCode,
            deviceCodeLoginVerificationUriStatus = loginDiagnostics.verificationUriStatus,
            deviceCodeLoginPollIntervalSeconds = loginDiagnostics.pollIntervalSeconds,
            deviceCodeLoginExpiresAt = loginDiagnostics.expiresAt?.toString(),
            workManagerStatus = wm.periodicStatus,
            notificationPermissionStatus = notificationPermissionReader.status(),
            generatedAtMillis = now,
            androidRelease = env.androidRelease,
            deviceModel = env.deviceModel,
            locale = env.locale,
            batteryOptimizationIgnored = env.batteryOptimizationIgnored,
            backgroundRestricted = env.backgroundRestricted,
            networkType = env.networkType,
            dataSaver = env.dataSaver,
            appFirstInstallAt = env.appFirstInstallAtMillis?.toString(),
            appLastUpdateAt = env.appLastUpdateAtMillis?.toString(),
            workManagerRunAttemptCount = wm.periodicRunAttemptCount,
            workManagerNextScheduleAt = wm.periodicNextScheduleAtMillis?.toString(),
            workManagerStopReason = wm.periodicStopReason,
            onceWorkManagerStatus = wm.onceStatus,
            accountSummaries = accountSummaries,
            roomSchemaVersion = roomSchemaVersion,
            retentionDays = when (retention) {
                RetentionPreference.SevenDays -> "7"
                RetentionPreference.ThirtyDays -> "30"
                RetentionPreference.NinetyDays -> "90"
                RetentionPreference.Forever -> "forever"
            },
            refreshIntervalMinutes = prefs.backgroundRefreshIntervalMinutes,
            statusNotificationEnabled = prefs.statusNotificationEnabled,
            quotaAlertsEnabled = prefs.quotaAlertsEnabled,
            alertThresholds = "caution=${prefs.cautionThreshold}," +
                "warning=${prefs.warningThreshold},limit=${prefs.limitThreshold}",
            accountAlertConfigs = accountAlertConfigs,
            widgetCount = widgetCount,
        )
    }

    /**
     * Every account gets the same detail. Only statuses leave the device: the session file is
     * reported as present or not, never its contents, and the account id only as a hash.
     */
    private suspend fun ProviderAccountEntity.diagnosticsSummary(): DiagnosticsAccountSummary {
        val attempt = refreshAttemptDao.getLatestForAccount(providerId, localAccountId)
        val snapshot = quotaSnapshotDao.getLatestForAccount(providerId, localAccountId)
        val session = runCatching {
            sessionStore.load(providerId = providerId, localAccountId = localAccountId)
        }.getOrNull()
        return DiagnosticsAccountSummary(
            providerId = providerId,
            accountIdHash = "sha256:${localAccountId.diagnosticsSafeHash()}",
            status = status,
            lastAttemptStatus = attempt?.status ?: "none",
            lastAttemptErrorCode = attempt?.errorCode,
            lastAttemptAt = attempt?.startedAt?.toString(),
            lastSuccessfulRefreshAt = lastSuccessfulRefreshAt?.toString(),
            latestSnapshotAt = snapshot?.fetchedAt?.toString(),
            sessionEnvelopeStatus = if (session == null) "missing_or_unreadable" else "present",
            sessionProviderAccountIdStatus = when {
                session == null -> null
                session.providerAccountId.isNullOrBlank() -> "missing"
                else -> "present"
            },
            consecutiveFailures = refreshAttemptDao.countConsecutiveFailuresSinceLatestSuccess(
                providerId = providerId,
                localAccountId = localAccountId,
            ),
            latestSnapshotSource = snapshot?.source,
            latestSnapshotDigestStatus = snapshot?.let { if (it.responseDigest == null) "missing" else "present" },
            lastAttemptTrigger = attempt?.trigger,
            httpStatus = attempt?.httpStatus,
            retryable = attempt?.retryable,
            userActionRequired = attempt?.userActionRequired,
            diagnosticsDigest = attempt?.diagnosticsDigest,
        )
    }
}

internal fun interface WorkManagerStatusReader {
    suspend fun status(): WorkManagerDiagnostics
}

internal fun interface NotificationPermissionReader {
    fun status(): String
}

private class AndroidWorkManagerStatusReader(
    context: Context,
) : WorkManagerStatusReader {
    private val appContext = context.applicationContext

    override suspend fun status(): WorkManagerDiagnostics = withContext(Dispatchers.IO) {
        val wm = WorkManager.getInstance(appContext)
        val periodicName = RefreshWorkScheduler.UNIQUE_PERIODIC_WORK_NAME
        val periodic = runCatching {
            wm.getWorkInfosForUniqueWork(periodicName).get(750, TimeUnit.MILLISECONDS)
        }.getOrNull()

        // No one-time unique work exists in this project; report a static sentinel.
        WorkManagerDiagnostics(
            periodicStatus = periodic.formatStatus(periodicName),
            periodicRunAttemptCount = periodic?.maxOfOrNull { it.runAttemptCount },
            periodicNextScheduleAtMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                periodic?.mapNotNull { info -> info.nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE } }
                    ?.minOrNull()
            } else {
                null
            },
            periodicStopReason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                periodic?.mapNotNull { info ->
                    info.stopReason.takeIf { it != WorkInfo.STOP_REASON_NOT_STOPPED }
                }?.firstOrNull()?.toString()
            } else {
                null
            },
            onceStatus = "not_enqueued:quota_refresh_once",
        )
    }

    private fun List<WorkInfo>?.formatStatus(uniqueName: String): String =
        when {
            this == null -> "query_failed:$uniqueName"
            isEmpty() -> "not_enqueued:$uniqueName"
            else -> "$uniqueName:" + joinToString(separator = ",") { it.state.name.lowercase() }
        }
}

private class AndroidNotificationPermissionReader(
    context: Context,
) : NotificationPermissionReader {
    private val appContext = context.applicationContext

    override fun status(): String =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            "not_required"
        } else if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "granted"
        } else {
            "denied"
        }
}

