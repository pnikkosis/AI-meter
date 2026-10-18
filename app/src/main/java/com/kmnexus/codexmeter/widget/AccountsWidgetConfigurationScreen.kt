package com.kmnexus.codexmeter.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing

internal data class AccountsWidgetAccountOption(
    val localAccountId: String,
    val providerName: String,
    val accountName: String,
    val hiddenOnHome: Boolean,
)

internal data class AccountsWidgetConfigurationScreenState(
    /** Every saved account, in Home's order. */
    val accounts: List<AccountsWidgetAccountOption> = emptyList(),
    val followsVisibleAccounts: Boolean = true,
    val chosenAccountIds: List<String> = emptyList(),
) {
    val canSave: Boolean
        get() = followsVisibleAccounts || chosenAccountIds.isNotEmpty()

    /** Switching to a choice starts from what the widget shows now: the accounts visible on Home. */
    fun choosingAccounts(): AccountsWidgetConfigurationScreenState =
        copy(
            followsVisibleAccounts = false,
            chosenAccountIds = chosenAccountIds.ifEmpty {
                accounts.filterNot { it.hiddenOnHome }.map { it.localAccountId }
            },
        )

    fun toggled(localAccountId: String): AccountsWidgetConfigurationScreenState =
        copy(chosenAccountIds = toggleAccountChoice(chosenAccountIds, localAccountId, accounts.map { it.localAccountId }))

    fun toConfiguration(): AccountsWidgetConfiguration =
        if (followsVisibleAccounts) AccountsWidgetConfiguration() else AccountsWidgetConfiguration(chosenAccountIds)

    companion object {
        fun from(
            accounts: List<AccountsWidgetAccountOption>,
            configuration: AccountsWidgetConfiguration,
        ): AccountsWidgetConfigurationScreenState {
            val known = accounts.map { it.localAccountId }
            val chosen = known.filter { it in configuration.chosenAccountIds }
            return AccountsWidgetConfigurationScreenState(
                accounts = accounts,
                followsVisibleAccounts = chosen.isEmpty(),
                chosenAccountIds = chosen,
            )
        }
    }
}

/** Adds or removes [localAccountId], keeping the accounts in [order] (Home's) whatever the tap order. */
internal fun toggleAccountChoice(current: List<String>, localAccountId: String, order: List<String>): List<String> {
    val next = if (localAccountId in current) current - localAccountId else current + localAccountId
    return order.filter { it in next }
}

@Composable
internal fun AccountsWidgetConfigurationRoute(
    appWidgetId: Int,
    loadState: suspend () -> AccountsWidgetConfigurationScreenState,
    onCancel: () -> Unit,
    onSave: (AccountsWidgetConfiguration) -> Unit,
) {
    var state by remember(appWidgetId) { mutableStateOf<AccountsWidgetConfigurationScreenState?>(null) }
    var isSaving by remember(appWidgetId) { mutableStateOf(false) }

    LaunchedEffect(appWidgetId) {
        state = loadState()
    }

    val currentState = state
    if (currentState == null) {
        WidgetConfigurationLoading()
        return
    }
    WidgetConfigurationDialogContainer {
        Column(
            modifier = Modifier
                .heightIn(max = WIDGET_CONFIG_DIALOG_MAX_HEIGHT)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.xs)) {
                Text(
                    text = stringResource(R.string.accounts_widget_config_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.accounts_widget_config_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (currentState.accounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.widget_config_no_accounts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ModeRow(
                    label = stringResource(R.string.settings_notification_account_all_visible),
                    selected = currentState.followsVisibleAccounts,
                    onClick = { state = currentState.copy(followsVisibleAccounts = true) },
                )
                ModeRow(
                    label = stringResource(R.string.accounts_widget_config_choose),
                    selected = !currentState.followsVisibleAccounts,
                    onClick = { state = currentState.choosingAccounts() },
                )
                if (!currentState.followsVisibleAccounts) {
                    Column(modifier = Modifier.padding(start = CodexMeterSpacing.lg)) {
                        currentState.accounts.forEach { account ->
                            AccountChoiceRow(
                                account = account,
                                checked = account.localAccountId in currentState.chosenAccountIds,
                                onClick = { state = currentState.toggled(account.localAccountId) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !isSaving, shape = CodexMeterShapes.md) {
                    Text(text = stringResource(R.string.widget_config_cancel))
                }
                Button(
                    onClick = {
                        isSaving = true
                        onSave(currentState.toConfiguration())
                    },
                    enabled = !isSaving && currentState.canSave,
                    shape = CodexMeterShapes.md,
                ) {
                    Text(
                        text = stringResource(
                            if (isSaving) R.string.widget_config_saving else R.string.widget_config_save,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AccountChoiceRow(account: AccountsWidgetAccountOption, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.accountName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (account.hiddenOnHome) {
                    stringResource(R.string.accounts_widget_config_hidden_format, account.providerName)
                } else {
                    account.providerName
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
