package com.kmnexus.codexmeter.ui.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeConnect
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackPages
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme

/**
 * Claude sign-in in the user's browser (Custom Tab or default browser), never in a WebView. The
 * browser keeps its own cookies; the app only receives the authorization code on the loopback port.
 */
@Composable
fun ClaudeLoginRoute(
    connector: ClaudeConnect,
    expectedProviderAccountId: ProviderAccountId?,
    onBack: () -> Unit,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClaudeLoginViewModel = viewModel(
        factory = ClaudeLoginViewModel.factory(
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

    ClaudeLoginScreen(
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
    )
}

@Composable
fun ClaudeLoginScreen(
    uiState: ClaudeLoginUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onContinue: () -> Unit = {},
    onReopenBrowser: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    AuthScaffold(
        title = stringResource(
            if (uiState.isRelogin) R.string.claude_login_relogin_title else R.string.claude_login_title,
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
            ClaudeDisclosureCard()
            ClaudeStatusCard(
                uiState = uiState,
                onContinue = onContinue,
                onReopenBrowser = onReopenBrowser,
                onCancel = onCancel,
                onDone = onDone,
            )
            Spacer(modifier = Modifier.height(CodexMeterSpacing.bottomNavigationClearance))
        }
    }
}

@Composable
private fun ClaudeDisclosureCard() {
    ClaudeSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm)) {
            Text(
                text = stringResource(R.string.claude_login_disclosure_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            listOf(
                R.string.claude_login_disclosure_browser,
                R.string.claude_login_disclosure_client,
                R.string.claude_login_disclosure_scope,
                R.string.claude_login_disclosure_storage,
                R.string.claude_login_disclosure_policy,
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
private fun ClaudeStatusCard(
    uiState: ClaudeLoginUiState,
    onContinue: () -> Unit,
    onReopenBrowser: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    ClaudeSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.md)) {
            when (uiState.phase) {
                ClaudeLoginPhase.Ready -> {
                    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.claude_login_continue))
                    }
                }
                ClaudeLoginPhase.WaitingForBrowser -> {
                    ProgressRow(stringResource(R.string.claude_login_waiting))
                    OutlinedButton(onClick = onReopenBrowser, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.claude_login_reopen))
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = CodexMeterShapes.md) {
                        Text(text = stringResource(R.string.add_account_cancel_login))
                    }
                }
                ClaudeLoginPhase.Connecting -> ProgressRow(stringResource(R.string.claude_login_connecting))
                ClaudeLoginPhase.Connected -> {
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
                ClaudeLoginPhase.Failed -> {
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
private fun ProgressRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClaudeSurfaceCard(content: @Composable () -> Unit) {
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

/**
 * Opens [url] in a Custom Tab of the user's browser (falls back to any browser). Returns false
 * when no app can handle it. The URL is only ever an https claude.com authorization page.
 */
private fun Context.openInBrowser(url: String): Boolean =
    try {
        CustomTabsIntent.Builder()
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(this, Uri.parse(url))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
