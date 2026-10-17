package com.kmnexus.codexmeter.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.kmnexus.codexmeter.ui.components.AppPullToRefreshIndicator
import com.kmnexus.codexmeter.ui.components.DashedAddButton
import com.kmnexus.codexmeter.ui.quota.shortMomentFormatter
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.account.AccountDeleteUseCase
import com.kmnexus.codexmeter.domain.account.AccountListUseCase
import com.kmnexus.codexmeter.domain.account.AccountRenameUseCase
import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.account.InMemoryAccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceStore
import com.kmnexus.codexmeter.data.currency.ExchangeRateReader
import com.kmnexus.codexmeter.domain.currency.CurrencyPreferenceReader
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing
import com.kmnexus.codexmeter.ui.theme.CodexMeterTypography
import kotlinx.coroutines.launch

@Composable
fun AccountRoute(
    accountListUseCase: AccountListUseCase,
    accountDeleteUseCase: AccountDeleteUseCase,
    accountRenameUseCase: AccountRenameUseCase,
    displayPreferenceStore: AccountDisplayPreferenceStore = InMemoryAccountDisplayPreferenceStore(),
    notificationPreferenceStore: NotificationPreferenceStore = AccountInMemoryNotificationPreferenceStore(),
    quotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester =
        NoopAccountQuotaAlertEvaluationRequester,
    refreshAllUseCase: AccountRefreshAllUseCase = NoopAccountRefreshAllUseCase,
    currencyPreferenceReader: CurrencyPreferenceReader = NoopAccountCurrencyPreferenceReader,
    exchangeRateReader: ExchangeRateReader = NoopAccountExchangeRateReader,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(
        factory = AccountViewModel.factory(
            deleteUseCase = accountDeleteUseCase,
            accountListUseCase = accountListUseCase,
            renameUseCase = accountRenameUseCase,
            displayPreferenceStore = displayPreferenceStore,
            notificationPreferenceStore = notificationPreferenceStore,
            quotaAlertEvaluationRequester = quotaAlertEvaluationRequester,
            refreshAllUseCase = refreshAllUseCase,
            currencyPreferenceReader = currencyPreferenceReader,
            exchangeRateReader = exchangeRateReader,
        ),
    ),
    onLoginToCodexClick: () -> Unit = {},
    onAddAccountClick: () -> Unit = viewModel::requestAddAccount,
    onReloginAccount: (com.kmnexus.codexmeter.domain.model.ProviderId, LocalAccountId, String?) -> Unit = { _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadAccounts()
    }
    LaunchedEffect(uiState.pendingReloginRequest) {
        val request = uiState.pendingReloginRequest ?: return@LaunchedEffect
        onReloginAccount(request.providerId, request.localAccountId, request.providerAccountId)
        viewModel.consumeReloginRequest()
    }
    AccountScreen(
        uiState = uiState,
        modifier = modifier,
        onRefresh = viewModel::refreshAllAccounts,
        onAddAccountClick = onAddAccountClick,
        onLoginToCodexClick = {
            viewModel.selectAddAccountLogin()
            onLoginToCodexClick()
        },
        onAddAccountDismiss = viewModel::dismissAddAccount,
        onVisibilityChange = { id, visible ->
            coroutineScope.launch {
                viewModel.setAccountVisible(id, visible)
            }
        },
        onMoveUpClick = { id ->
            coroutineScope.launch {
                viewModel.moveAccount(id, offset = -1)
            }
        },
        onMoveDownClick = { id ->
            coroutineScope.launch {
                viewModel.moveAccount(id, offset = 1)
            }
        },
        onRenameClick = viewModel::requestRenameAccount,
        onRenameConfirm = { id, name ->
            coroutineScope.launch {
                viewModel.renameAccount(id, name)
            }
        },
        onRenameDismiss = viewModel::cancelRename,
        onReloginClick = { id -> viewModel.selectRelogin(id) },
        onQuotaAlertToggle = { id, windowId, enabled ->
            coroutineScope.launch {
                viewModel.setQuotaAlertEnabled(id, windowId, enabled)
            }
        },
        onDeleteClick = viewModel::requestDeleteAccount,
        onToggleExpanded = viewModel::toggleAccountExpanded,
        onDeleteConfirm = {
            coroutineScope.launch {
                viewModel.confirmDelete()
            }
        },
        onDeleteDismiss = viewModel::cancelDelete,
        onDisconnectNoticeDismiss = viewModel::dismissDisconnectNotice,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    uiState: AccountUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onAddAccountClick: () -> Unit = {},
    onLoginToCodexClick: () -> Unit = {},
    onAddAccountDismiss: () -> Unit = {},
    onVisibilityChange: (LocalAccountId, Boolean) -> Unit = { _, _ -> },
    onMoveUpClick: (LocalAccountId) -> Unit = {},
    onMoveDownClick: (LocalAccountId) -> Unit = {},
    onRenameClick: (LocalAccountId) -> Unit = {},
    onRenameConfirm: (LocalAccountId, String) -> Unit = { _, _ -> },
    onRenameDismiss: () -> Unit = {},
    onReloginClick: (LocalAccountId) -> Unit = {},
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit = { _, _, _ -> },
    onDeleteClick: (LocalAccountId) -> Unit = {},
    onToggleExpanded: (LocalAccountId) -> Unit = {},
    onDeleteConfirm: () -> Unit = {},
    onDeleteDismiss: () -> Unit = {},
    onDisconnectNoticeDismiss: () -> Unit = {},
) {
    val pullState = rememberPullToRefreshState()
    val moment = shortMomentFormatter()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            AppPullToRefreshIndicator(
                isRefreshing = uiState.isRefreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CodexMeterSpacing.xl, vertical = CodexMeterSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.lg),
    ) {
        AccountHeader()

        if (uiState.contentStatus == AccountContentStatus.Unauthenticated) {
            EmptyAccountsCard(onAddAccountClick = onAddAccountClick)
        } else {
            uiState.accounts.forEach { account ->
                AccountCard(
                    account = account,
                    moment = moment,
                    onToggleExpanded = onToggleExpanded,
                    onVisibilityChange = onVisibilityChange,
                    onMoveUpClick = onMoveUpClick,
                    onMoveDownClick = onMoveDownClick,
                    onRenameClick = onRenameClick,
                    onReloginClick = onReloginClick,
                    onQuotaAlertToggle = onQuotaAlertToggle,
                    onDeleteClick = onDeleteClick,
                )
            }
            DashedAddButton(text = stringResource(R.string.account_add_account), onClick = onAddAccountClick)
        }

        Spacer(modifier = Modifier.height(CodexMeterSpacing.bottomNavigationClearance))
    }
    }

    if (uiState.showAddAccountSheet) {
        AddAccountDialog(
            onLoginToCodexClick = onLoginToCodexClick,
            onDismiss = onAddAccountDismiss,
        )
    }

    uiState.disconnectNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = onDisconnectNoticeDismiss,
            title = { Text(text = stringResource(R.string.account_disconnected_title)) },
            text = {
                Text(
                    text = stringResource(notice.messageResId, notice.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = onDisconnectNoticeDismiss) {
                    Text(text = stringResource(R.string.account_disconnected_ok))
                }
            },
        )
    }

    uiState.pendingDeleteAccount?.let { pending ->
        DeleteAccountDialog(
            pending = pending,
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }

    uiState.pendingRenameAccount?.let { pending ->
        RenameAccountDialog(
            pending = pending,
            onConfirm = onRenameConfirm,
            onDismiss = onRenameDismiss,
        )
    }
}

@Composable
private fun AddAccountDialog(
    onLoginToCodexClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.account_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.md)) {
                Text(
                    text = stringResource(R.string.account_add_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onLoginToCodexClick, shape = CodexMeterShapes.md) {
                    Text(text = stringResource(R.string.account_add_login_to_codex))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_cancel))
            }
        },
    )
}

@Composable
private fun AccountHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.xs)) {
        Text(
            text = stringResource(R.string.account_title),
            style = CodexMeterTypography.display,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.account_screen_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RenameAccountDialog(
    pending: AccountRenameUi,
    onConfirm: (LocalAccountId, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(pending.accountId) { mutableStateOf(pending.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.account_rename_dialog_title)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(text = stringResource(R.string.account_rename_field_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pending.accountId, name) }) {
                Text(text = stringResource(R.string.account_rename_dialog_confirm))
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
private fun DeleteAccountDialog(
    pending: AccountDeleteConfirmationUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.account_delete_dialog_title)) },
        text = {
            Text(
                text = stringResource(pending.messageResId, pending.displayName),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.account_delete_dialog_confirm),
                    color = CodexMeterTheme.colors.danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.account_delete_dialog_cancel))
            }
        },
    )
}
