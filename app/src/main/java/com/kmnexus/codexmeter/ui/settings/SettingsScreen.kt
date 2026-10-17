package com.kmnexus.codexmeter.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.app.NotificationWindowChoicesLoader
import com.kmnexus.codexmeter.domain.account.AccountListUseCase
import com.kmnexus.codexmeter.domain.account.NoopAccountListUseCase
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceStore
import com.kmnexus.codexmeter.domain.theme.AppearancePreferenceStore
import com.kmnexus.codexmeter.domain.theme.ThemeMode
import com.kmnexus.codexmeter.domain.settings.NoopQuotaHistoryClearUseCase
import com.kmnexus.codexmeter.domain.settings.QuotaHistoryClearUseCase
import com.kmnexus.codexmeter.domain.settings.RetentionPreferenceStore
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing
import com.kmnexus.codexmeter.ui.theme.CodexMeterTypography
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    accountListUseCase: AccountListUseCase = NoopAccountListUseCase,
    retentionPreferenceStore: RetentionPreferenceStore = InMemoryRetentionPreferenceStore(),
    notificationPreferenceStore: NotificationPreferenceStore = InMemoryNotificationPreferenceStore(),
    backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader = InMemoryBackgroundRefreshStatusReader(),
    diagnosticsReader: SettingsDiagnosticsReader = DefaultSettingsDiagnosticsReader,
    quotaHistoryClearUseCase: QuotaHistoryClearUseCase = NoopQuotaHistoryClearUseCase,
    backgroundRefreshScheduler: BackgroundRefreshScheduler = NoopBackgroundRefreshScheduler,
    notificationWindowChoicesLoader: NotificationWindowChoicesLoader =
        NotificationWindowChoicesLoader { _, _ -> emptyList() },
    appearancePreferenceStore: AppearancePreferenceStore = NoopAppearancePreferenceStore,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            accountListUseCase = accountListUseCase,
            retentionPreferenceStore = retentionPreferenceStore,
            notificationPreferenceStore = notificationPreferenceStore,
            backgroundRefreshStatusReader = backgroundRefreshStatusReader,
            diagnosticsReader = diagnosticsReader,
            quotaHistoryClearUseCase = quotaHistoryClearUseCase,
            backgroundRefreshScheduler = backgroundRefreshScheduler,
            notificationWindowChoicesLoader = notificationWindowChoicesLoader,
            appearancePreferenceStore = appearancePreferenceStore,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val coroutineScope = rememberCoroutineScope()
    val diagnosticsClipboardLabel = stringResource(R.string.settings_diagnostics_clipboard_label)
    var pendingPermissionTarget by remember { mutableStateOf<SettingsNotificationPermissionTarget?>(null) }
    // What Android allows, re-read whenever the screen comes back (from the permission dialog or the
    // system settings): the switches never claim a notification Android would not show.
    var notificationsEnabled by remember { mutableStateOf(context.areAppNotificationsEnabled()) }
    var pendingSystemSettingsTarget by remember { mutableStateOf<SettingsNotificationPermissionTarget?>(null) }
    var rationaleBeforeRequest by remember { mutableStateOf(false) }
    fun enableNotificationTarget(target: SettingsNotificationPermissionTarget) {
        when (target) {
            SettingsNotificationPermissionTarget.StatusNotification -> viewModel.setStatusNotificationEnabled(true)
            SettingsNotificationPermissionTarget.AccountErrors -> viewModel.setAccountErrorsEnabled(true)
        }
    }
    fun openNotificationSettings(target: SettingsNotificationPermissionTarget) {
        pendingSystemSettingsTarget = target
        runCatching {
            context.startActivity(SettingsNotificationSettingsTarget.appNotificationSettingsIntent(context.packageName))
        }.onFailure { pendingSystemSettingsTarget = null }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = context.areAppNotificationsEnabled()
                // Kept until notifications are allowed: the screen also resumes right before the
                // system settings page opens.
                val target = pendingSystemSettingsTarget
                if (target != null && notificationsEnabled) {
                    pendingSystemSettingsTarget = null
                    enableNotificationTarget(target)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var batteryOptimizationIgnored by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    // The system dialog always returns RESULT_CANCELED, so re-read PowerManager on return instead of
    // trusting the result code to decide whether the hint should disappear.
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        batteryOptimizationIgnored = context.isIgnoringBatteryOptimizations()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val target = pendingPermissionTarget
        pendingPermissionTarget = null
        notificationsEnabled = context.areAppNotificationsEnabled()
        if (target != null) {
            if (granted) {
                enableNotificationTarget(target)
            } else if (
                SettingsNotificationPermissionGate.shouldOpenSystemSettingsAfterDenial(
                    rationaleBeforeRequest = rationaleBeforeRequest,
                    rationaleAfterRequest = context.shouldShowNotificationRationale(),
                )
            ) {
                openNotificationSettings(target)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.loadSettings()
    }
    var licensesVisible by rememberSaveable { mutableStateOf(false) }
    fun requestPermissionOrEnable(target: SettingsNotificationPermissionTarget, requestedEnabled: Boolean) {
        val permissionGranted = context.hasNotificationPermission()
        notificationsEnabled = context.areAppNotificationsEnabled()
        if (
            SettingsNotificationPermissionGate.shouldRequestPermission(
                sdkInt = Build.VERSION.SDK_INT,
                notificationPermissionGranted = permissionGranted,
                requestedEnabled = requestedEnabled,
            )
        ) {
            pendingPermissionTarget = target
        } else if (
            SettingsNotificationPermissionGate.shouldOpenSystemSettings(
                sdkInt = Build.VERSION.SDK_INT,
                notificationPermissionGranted = permissionGranted,
                notificationsEnabled = notificationsEnabled,
                requestedEnabled = requestedEnabled,
            )
        ) {
            openNotificationSettings(target)
        } else {
            when (target) {
                SettingsNotificationPermissionTarget.StatusNotification ->
                    viewModel.setStatusNotificationEnabled(requestedEnabled)
                SettingsNotificationPermissionTarget.AccountErrors ->
                    viewModel.setAccountErrorsEnabled(requestedEnabled)
            }
        }
    }
    SettingsScreen(
        uiState = uiState,
        modifier = modifier,
        onChoiceDialogRequested = viewModel::requestChoiceDialog,
        onChoiceDialogDismiss = viewModel::dismissChoiceDialog,
        onPersistentNotificationAccountSelected = viewModel::updatePersistentNotificationAccount,
        onPersistentNotificationWindowSelected = viewModel::updatePersistentNotificationWindow,
        notificationsEnabled = notificationsEnabled,
        onStatusNotificationChanged = { enabled ->
            requestPermissionOrEnable(SettingsNotificationPermissionTarget.StatusNotification, enabled)
        },
        onAccountErrorsChanged = { enabled ->
            requestPermissionOrEnable(SettingsNotificationPermissionTarget.AccountErrors, enabled)
        },
        onCautionThresholdChanged = { caution ->
            viewModel.updateAlertThresholds(caution, uiState.alerts.thresholds.warning)
        },
        onWarningThresholdChanged = { warning ->
            viewModel.updateAlertThresholds(uiState.alerts.thresholds.caution, warning)
        },
        onBackgroundRefreshChanged = viewModel::setBackgroundRefreshEnabled,
        onRefreshIntervalSelected = viewModel::updateRefreshInterval,
        onThemeModeSelected = viewModel::updateThemeMode,
        onRetentionSelected = viewModel::updateRetention,
        onClearCurrentHistoryClick = viewModel::requestClearCurrentHistory,
        onClearAllHistoryClick = viewModel::requestClearAllHistory,
        onDataActionConfirm = viewModel::confirmDataAction,
        onDataActionDismiss = viewModel::cancelDataAction,
        onDiagnosticsToggle = viewModel::toggleDiagnosticsExpanded,
        onDiagnosticsCopyClick = {
            coroutineScope.launch {
                val copyModel = viewModel.copyDiagnostics()
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(diagnosticsClipboardLabel, copyModel.text),
                )
            }
        },
        onDiagnosticsRecheckClick = viewModel::requestDiagnosticsRecheck,
        onAboutLicensesClick = { licensesVisible = true },
        showBatteryOptimizationHint = SettingsBatteryOptimizationGate.shouldOfferExemption(
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            backgroundRefreshEnabled = uiState.refresh.backgroundRefreshEnabled,
        ),
        onBatteryOptimizationClick = {
            runCatching {
                batteryOptimizationLauncher.launch(
                    SettingsBatteryOptimizationTarget.requestExemptionIntent(context.packageName),
                )
            }
        },
    )
    if (licensesVisible) {
        LicensesDialog(onDismiss = { licensesVisible = false })
    }
    pendingPermissionTarget?.let {
        NotificationPermissionRationaleDialog(
            onConfirm = {
                rationaleBeforeRequest = context.shouldShowNotificationRationale()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                pendingPermissionTarget = null
            },
        )
    }
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    modifier: Modifier = Modifier,
    onChoiceDialogRequested: (SettingsChoiceDialog) -> Unit = {},
    onChoiceDialogDismiss: () -> Unit = {},
    onPersistentNotificationAccountSelected: (SettingsNotificationAccountSelection) -> Unit = {},
    onPersistentNotificationWindowSelected: (QuotaWindowId) -> Unit = {},
    notificationsEnabled: Boolean = true,
    onStatusNotificationChanged: (Boolean) -> Unit = {},
    onAccountErrorsChanged: (Boolean) -> Unit = {},
    onCautionThresholdChanged: (Int) -> Unit = {},
    onWarningThresholdChanged: (Int) -> Unit = {},
    onBackgroundRefreshChanged: (Boolean) -> Unit = {},
    onRefreshIntervalSelected: (SettingsRefreshInterval) -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onRetentionSelected: (SettingsRetentionOption) -> Unit = {},
    onClearCurrentHistoryClick: () -> Unit = {},
    onClearAllHistoryClick: () -> Unit = {},
    onDataActionConfirm: () -> Unit = {},
    onDataActionDismiss: () -> Unit = {},
    onDiagnosticsToggle: () -> Unit = {},
    onDiagnosticsCopyClick: () -> Unit = {},
    onDiagnosticsRecheckClick: () -> Unit = {},
    onAboutLicensesClick: () -> Unit = {},
    showBatteryOptimizationHint: Boolean = false,
    onBatteryOptimizationClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CodexMeterSpacing.xl, vertical = CodexMeterSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.lg),
    ) {
        SettingsHeader()
        SettingsSectionLabel(R.string.settings_group_appearance)
        AppearanceCard(
            appearance = uiState.appearance,
            onThemeModeSelected = onThemeModeSelected,
        )
        SettingsSectionLabel(R.string.settings_group_persistent_notification)
        PersistentNotificationCard(
            persistentNotification = uiState.persistentNotification,
            notificationsEnabled = notificationsEnabled,
            onEnabledChanged = onStatusNotificationChanged,
            onAccountClick = { onChoiceDialogRequested(SettingsChoiceDialog.NotificationAccount) },
            onWindowClick = { onChoiceDialogRequested(SettingsChoiceDialog.NotificationWindow) },
        )
        SettingsSectionLabel(R.string.settings_group_alerts)
        AlertsCard(
            alerts = uiState.alerts,
            notificationsEnabled = notificationsEnabled,
            onAccountErrorsChanged = onAccountErrorsChanged,
            onCautionThresholdChanged = onCautionThresholdChanged,
            onWarningThresholdChanged = onWarningThresholdChanged,
        )
        SettingsSectionLabel(R.string.settings_group_refresh)
        RefreshCard(
            refresh = uiState.refresh,
            onBackgroundRefreshChanged = onBackgroundRefreshChanged,
            onIntervalClick = { onChoiceDialogRequested(SettingsChoiceDialog.RefreshInterval) },
            showBatteryOptimizationHint = showBatteryOptimizationHint,
            onBatteryOptimizationClick = onBatteryOptimizationClick,
        )
        SettingsSectionLabel(R.string.settings_group_data)
        DataCard(
            data = uiState.data,
            onRetentionClick = { onChoiceDialogRequested(SettingsChoiceDialog.Retention) },
            onClearCurrentHistoryClick = onClearCurrentHistoryClick,
            onClearAllHistoryClick = onClearAllHistoryClick,
        )
        SettingsSectionLabel(R.string.settings_group_diagnostics)
        DiagnosticsCard(uiState.diagnostics, onDiagnosticsToggle, onDiagnosticsCopyClick, onDiagnosticsRecheckClick)
        SettingsSectionLabel(R.string.settings_group_about)
        AboutCard(uiState.about, onAboutLicensesClick)
        Spacer(modifier = Modifier.height(CodexMeterSpacing.bottomNavigationClearance))
    }

    uiState.data.pendingAction?.let { action ->
        ConfirmDataActionDialog(action, onDataActionConfirm, onDataActionDismiss)
    }
    uiState.pendingChoiceDialog?.let { dialog ->
        SettingsChoiceDialogContent(
            dialog = dialog,
            uiState = uiState,
            onPersistentNotificationAccountSelected = onPersistentNotificationAccountSelected,
            onPersistentNotificationWindowSelected = onPersistentNotificationWindowSelected,
            onRetentionSelected = onRetentionSelected,
            onRefreshIntervalSelected = onRefreshIntervalSelected,
            onDismiss = onChoiceDialogDismiss,
        )
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm)) {
        Text(
            text = stringResource(R.string.settings_title),
            style = CodexMeterTypography.current.display,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SwitchRow(
    @StringRes titleResId: Int,
    @StringRes descriptionResId: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsItemText(titleResId, descriptionResId, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
internal fun DestructiveActionRow(@StringRes titleResId: Int, @StringRes descriptionResId: Int, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsItemText(titleResId, descriptionResId, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, shape = CodexMeterShapes.md) {
            Text(text = stringResource(R.string.settings_data_clear_action), color = CodexMeterTheme.colors.danger)
        }
    }
}

@Composable
internal fun SettingsItemText(@StringRes titleResId: Int, @StringRes descriptionResId: Int?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.xs)) {
        Text(text = stringResource(titleResId), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (descriptionResId != null) {
            Text(text = stringResource(descriptionResId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OptionColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm), content = content)
}

@Composable
internal fun OptionButton(@StringRes labelResId: Int, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
            Text(text = stringResource(labelResId))
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
            Text(text = stringResource(labelResId))
        }
    }
}

@Composable
internal fun OptionTextButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
            Text(text = label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
            Text(text = label)
        }
    }
}

@Composable
private fun ConfirmDataActionDialog(
    action: SettingsPendingDataAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(action.titleResId)) },
        text = { Text(text = stringResource(action.messageResId)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.settings_data_confirm_clear), color = CodexMeterTheme.colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

/**
 * Shows the upstream CodexMeter MIT notice inside the app, as its license requires for copies and
 * substantial portions of the software.
 */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_about_licenses_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm),
            ) {
                Text(text = stringResource(R.string.settings_about_based_on))
                Text(
                    text = stringResource(R.string.license_codexmeter_mit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_about_third_party_libraries),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_about_licenses_close))
            }
        },
    )
}

@Composable
private fun SettingsChoiceDialogContent(
    dialog: SettingsChoiceDialog,
    uiState: SettingsUiState,
    onPersistentNotificationAccountSelected: (SettingsNotificationAccountSelection) -> Unit,
    onPersistentNotificationWindowSelected: (QuotaWindowId) -> Unit,
    onRetentionSelected: (SettingsRetentionOption) -> Unit,
    onRefreshIntervalSelected: (SettingsRefreshInterval) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    when (dialog) {
                        SettingsChoiceDialog.NotificationAccount -> R.string.settings_notification_account_title
                        SettingsChoiceDialog.NotificationWindow -> R.string.settings_notification_display_quota_title
                        SettingsChoiceDialog.Retention -> R.string.settings_retention_title
                        SettingsChoiceDialog.RefreshInterval -> R.string.settings_refresh_interval_title
                    },
                ),
            )
        },
        text = {
            OptionColumn {
                when (dialog) {
                    SettingsChoiceDialog.NotificationAccount ->
                        uiState.persistentNotification.accountChoices.forEach { choice ->
                            OptionTextButton(
                                label = choice.selection.displayLabel(),
                                selected = uiState.persistentNotification.accountSelection == choice.selection,
                            ) {
                                onPersistentNotificationAccountSelected(choice.selection)
                            }
                        }
                    SettingsChoiceDialog.NotificationWindow ->
                        uiState.persistentNotification.windowChoices.forEach { choice ->
                            OptionButton(
                                choice.labelResId,
                                uiState.persistentNotification.selectedWindowId == choice.windowId,
                            ) {
                                onPersistentNotificationWindowSelected(choice.windowId)
                            }
                        }
                    SettingsChoiceDialog.Retention -> SettingsRetentionOption.entries.forEach { option ->
                        OptionButton(option.labelResId, uiState.data.retention == option) {
                            onRetentionSelected(option)
                        }
                    }
                    SettingsChoiceDialog.RefreshInterval -> SettingsRefreshInterval.entries.forEach { option ->
                        OptionButton(option.labelResId, uiState.refresh.interval == option) {
                            onRefreshIntervalSelected(option)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
private fun SettingsSectionLabel(@StringRes resId: Int) {
    Text(text = stringResource(resId), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NotificationPermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_notification_permission_title)) },
        text = { Text(text = stringResource(R.string.settings_notification_permission_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.settings_notification_permission_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
internal fun SettingsSurfaceCard(content: @Composable () -> Unit) {
    Card(
        shape = CodexMeterShapes.xl,
        colors = CardDefaults.cardColors(containerColor = CodexMeterTheme.colors.surface),
        border = BorderStroke(1.dp, CodexMeterTheme.colors.border),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(CodexMeterSpacing.lg)) {
            content()
        }
    }
}

private enum class SettingsNotificationPermissionTarget {
    StatusNotification,
    AccountErrors,
}

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

/** False when Android blocks the app's notifications: permission not granted (13+) or turned off. */
private fun Context.areAppNotificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.shouldShowNotificationRationale(): Boolean {
    val activity = findActivity() ?: return false
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Null when PowerManager is unavailable so the hint stays hidden rather than guessing.
private fun Context.isIgnoringBatteryOptimizations(): Boolean? =
    (getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(packageName)
