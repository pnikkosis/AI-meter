package com.kmnexus.codexmeter.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnect
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackPages
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme

/**
 * Codex sign-in in the user's browser (Custom Tab or default browser), as `codex login` does it. The
 * browser keeps its own cookies; the app only receives the authorization code on the loopback port.
 * The device-code sign-in stays reachable through [onUseDeviceCode].
 */
@Composable
fun CodexLoginRoute(
    connector: CodexBrowserConnect,
    expectedProviderAccountId: ProviderAccountId?,
    onBack: () -> Unit,
    onConnected: () -> Unit,
    onUseDeviceCode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodexLoginViewModel = viewModel(
        factory = CodexLoginViewModel.factory(
            connector = connector,
            expectedProviderAccountId = expectedProviderAccountId,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pages = CallbackPages.simple(
        successTitle = stringResource(R.string.claude_login_page_success_title),
        successMessage = stringResource(R.string.claude_login_page_success_message),
        failureTitle = stringResource(R.string.claude_login_page_failure_title),
        failureMessage = stringResource(R.string.claude_login_page_failure_message),
    )
    LaunchedEffect(viewModel) {
        viewModel.browserRequests.collect { url ->
            if (!context.openInBrowser(url)) viewModel.onBrowserUnavailable()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenVisibilityChanged(visible = true)
                Lifecycle.Event.ON_STOP -> viewModel.onScreenVisibilityChanged(visible = false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CodexLoginScreen(
        uiState = uiState,
        modifier = modifier,
        onBack = {
            viewModel.cancel()
            onBack()
        },
        onContinue = { viewModel.start(pages) },
        onReopenBrowser = viewModel::reopenBrowser,
        onCancel = viewModel::cancel,
        onDone = onConnected,
        onUseDeviceCode = {
            viewModel.cancel()
            onUseDeviceCode()
        },
    )
}

@Composable
fun CodexLoginScreen(
    uiState: CodexLoginUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onContinue: () -> Unit = {},
    onReopenBrowser: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDone: () -> Unit = {},
    onUseDeviceCode: () -> Unit = {},
) {
    AuthScaffold(
        title = stringResource(
            if (uiState.isRelogin) R.string.codex_login_relogin_title else R.string.codex_login_title,
        ),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CodexMeterSpacing.xl)
                .padding(top = CodexMeterSpacing.sm, bottom = CodexMeterSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.lg),
        ) {
            CodexDisclosureCard()
            CodexStatusCard(
                uiState = uiState,
                onContinue = onContinue,
                onReopenBrowser = onReopenBrowser,
                onCancel = onCancel,
                onDone = onDone,
            )
            if (uiState.phase == CodexLoginPhase.Ready || uiState.phase == CodexLoginPhase.Failed) {
                DeviceCodeAlternative(onUseDeviceCode = onUseDeviceCode)
            }
            Spacer(modifier = Modifier.height(CodexMeterSpacing.bottomNavigationClearance))
        }
    }
}

@Composable
private fun CodexDisclosureCard() {
    CodexSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm)) {
            Text(
                text = stringResource(R.string.claude_login_disclosure_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            listOf(
                R.string.codex_login_disclosure_browser,
                R.string.codex_login_disclosure_client,
                R.string.codex_login_disclosure_scope,
                R.string.codex_login_disclosure_storage,
            ).forEach { resId ->
                Text(
                    text = stringResource(resId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodexStatusCard(
    uiState: CodexLoginUiState,
    onContinue: () -> Unit,
    onReopenBrowser: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    CodexSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.md)) {
            when (uiState.phase) {
                CodexLoginPhase.Ready -> {
                    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.claude_login_continue))
                    }
                }
                CodexLoginPhase.WaitingForBrowser -> {
                    ProgressRow(stringResource(R.string.claude_login_waiting))
                    OutlinedButton(onClick = onReopenBrowser, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.claude_login_reopen))
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.add_account_cancel_login))
                    }
                }
                CodexLoginPhase.Connecting -> ProgressRow(stringResource(R.string.codex_login_connecting))
                CodexLoginPhase.Connected -> {
                    Text(
                        text = stringResource(
                            R.string.add_account_device_code_connected,
                            uiState.connectedAccountName.orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CodexMeterTheme.colors.success,
                    )
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.claude_login_done))
                    }
                }
                CodexLoginPhase.Failed -> {
                    uiState.failure?.let { failure ->
                        Text(
                            text = stringResource(failure.messageResId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    uiState.mismatchEmail?.let { email ->
                        Text(
                            text = stringResource(R.string.claude_login_mismatch_signed_in_as, email),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.claude_login_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCodeAlternative(onUseDeviceCode: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.xs)) {
        TextButton(onClick = onUseDeviceCode) {
            Text(text = stringResource(R.string.codex_login_use_device_code))
        }
        Text(
            text = stringResource(R.string.codex_login_device_code_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CodexMeterSpacing.md),
        )
    }
}

@Composable
private fun CodexSurfaceCard(content: @Composable () -> Unit) {
    Card(
        shape = CodexMeterShapes.xl,
        colors = CardDefaults.cardColors(containerColor = CodexMeterTheme.colors.surface),
        border = BorderStroke(1.dp, CodexMeterTheme.colors.border),
    ) {
        Box(modifier = Modifier.padding(CodexMeterSpacing.lg)) {
            content()
        }
    }
}
