package com.kmnexus.codexmeter.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.ui.components.AppCard
import com.kmnexus.codexmeter.ui.components.ProviderChip
import com.kmnexus.codexmeter.ui.motion.CodexMeterMotion
import com.kmnexus.codexmeter.ui.motion.rememberCodexMeterAnimatorsEnabled
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelText
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale

@Composable
internal fun EmptyAccountsCard(onAddAccountClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.account_no_accounts_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.account_no_accounts_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAddAccountClick, shape = CodexMeterShapes.md) {
            Text(text = stringResource(R.string.account_add_account))
        }
    }
}

@Composable
internal fun AccountCard(
    account: AccountItemUi,
    moment: (Instant) -> String,
    onToggleExpanded: (LocalAccountId) -> Unit,
    onVisibilityChange: (LocalAccountId, Boolean) -> Unit,
    onMoveUpClick: (LocalAccountId) -> Unit,
    onMoveDownClick: (LocalAccountId) -> Unit,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    val animatorsEnabled = rememberCodexMeterAnimatorsEnabled()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        val toggleLabel = stringResource(
            if (account.isExpanded) R.string.account_collapse else R.string.account_expand,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = toggleLabel) { onToggleExpanded(account.id) },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderChip(name = account.providerName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = account.subtitle(moment),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            account.badgeLabelResId?.let { badge ->
                StatusBadge(labelResId = badge, tone = account.tone)
            }
            ExpandChevron(isExpanded = account.isExpanded, animatorsEnabled = animatorsEnabled)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = account.isVisible,
                    role = Role.Switch,
                    onValueChange = { visible -> onVisibilityChange(account.id, visible) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_show_on_home),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // The whole row toggles; the switch only mirrors the state.
            Switch(checked = account.isVisible, onCheckedChange = null)
        }
        AccountDetailsDrawer(
            account = account,
            animatorsEnabled = animatorsEnabled,
            onMoveUpClick = onMoveUpClick,
            onMoveDownClick = onMoveDownClick,
            onRenameClick = onRenameClick,
            onReloginClick = onReloginClick,
            onQuotaAlertToggle = onQuotaAlertToggle,
            onDeleteClick = onDeleteClick,
        )
    }
}

@Composable
private fun AccountItemUi.subtitle(moment: (Instant) -> String): String {
    val refresh = lastSuccessfulRefreshAt?.let { stringResource(R.string.account_last_refresh_format, moment(it)) }
        ?: stringResource(R.string.account_last_refresh_unavailable)
    return listOfNotNull(planType, refresh).joinToString(" · ")
}

@Composable
private fun ExpandChevron(isExpanded: Boolean, animatorsEnabled: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = if (animatorsEnabled) {
            tween(
                durationMillis = CodexMeterMotion.AccountDrawerDurationMillis,
                easing = FastOutSlowInEasing,
            )
        } else {
            snap()
        },
        label = "account_expand_icon_rotation",
    )
    Icon(
        painter = painterResource(R.drawable.ic_chevron_down),
        // The row it sits in carries the expand/collapse label.
        contentDescription = null,
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer { rotationZ = rotation },
        tint = CodexMeterTheme.colors.secondary,
    )
}

@Composable
private fun AccountDetailsDrawer(
    account: AccountItemUi,
    animatorsEnabled: Boolean,
    onMoveUpClick: (LocalAccountId) -> Unit,
    onMoveDownClick: (LocalAccountId) -> Unit,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    AnimatedVisibility(
        visible = account.isExpanded,
        modifier = Modifier.clipToBounds(),
        enter = if (animatorsEnabled) {
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(
                    durationMillis = CodexMeterMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = CodexMeterMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            EnterTransition.None
        },
        exit = if (animatorsEnabled) {
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(
                    durationMillis = CodexMeterMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = CodexMeterMotion.AccountDrawerDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            ExitTransition.None
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.md)) {
            HorizontalDivider(thickness = 1.dp, color = CodexMeterTheme.colors.border)
            if (account.credits !is AccountCreditsUi.Unavailable) {
                CreditsLine(account.credits)
            }
            if (account.quotaSummaries.isNotEmpty()) {
                QuotaSummaryRow(account.quotaSummaries)
            }
            if (account.modelFamilies.isNotEmpty()) {
                ModelDetailsSection(account.modelFamilies)
            }
            if (account.alertToggles.isNotEmpty()) {
                QuotaAlertSwitches(
                    account = account,
                    onQuotaAlertToggle = onQuotaAlertToggle,
                )
            }
            AccountActions(
                account = account,
                onMoveUpClick = onMoveUpClick,
                onMoveDownClick = onMoveDownClick,
                onRenameClick = onRenameClick,
                onReloginClick = onReloginClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun CreditsLine(credits: AccountCreditsUi) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.account_credits_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = credits.displayText(locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuotaSummaryRow(summaries: List<AccountQuotaSummaryUi>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexMeterSpacing.md),
    ) {
        summaries.forEach { summary ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(CodexMeterShapes.md)
                    .background(CodexMeterTheme.colors.surfaceSoft)
                    .padding(CodexMeterSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.xs),
            ) {
                Text(
                    text = if (summary.valueText == null && summary.percent != null) {
                        // Percent values are the share LEFT in the window.
                        stringResource(
                            R.string.quota_window_remaining_format,
                            quotaWindowLabelText(summary.labelResId, summary.labelName),
                        )
                    } else {
                        quotaWindowLabelText(summary.labelResId, summary.labelName)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary.valueText
                        ?: summary.percent?.let {
                            stringResource(R.string.account_quota_percent_format, it)
                        }
                        ?: stringResource(R.string.account_quota_placeholder_no_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Original native balance (shown when conversion occurred)
                summary.originalValueText?.let { original ->
                    Text(
                        text = original,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // DeepSeek-style granted/topped-up breakdown (native currency)
                if (summary.grantedText != null || summary.toppedUpText != null) {
                    Text(
                        text = stringResource(
                            R.string.quota_balance_breakdown_format,
                            summary.grantedText.orEmpty(),
                            summary.toppedUpText.orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelDetailsSection(families: List<AccountModelFamilyUi>) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CodexMeterShapes.md)
            .background(CodexMeterTheme.colors.surfaceSoft)
            .padding(CodexMeterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_model_details_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            families.forEach { family ->
                Text(
                    text = family.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                family.models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = CodexMeterSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.account_quota_percent_format, model.remainingPercent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaAlertSwitches(
    account: AccountItemUi,
    onQuotaAlertToggle: (LocalAccountId, QuotaWindowId, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CodexMeterShapes.md)
            .background(CodexMeterTheme.colors.surfaceSoft)
            .padding(CodexMeterSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.account_quota_alerts_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        account.alertToggles.forEach { toggle ->
            QuotaAlertSwitchLine(
                label = quotaWindowLabelText(toggle.labelResId, toggle.labelName),
                checked = toggle.enabled,
                onCheckedChange = { enabled ->
                    onQuotaAlertToggle(account.id, QuotaWindowId(toggle.windowId), enabled)
                },
            )
        }
    }
}

@Composable
private fun QuotaAlertSwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun AccountActions(
    account: AccountItemUi,
    onMoveUpClick: (LocalAccountId) -> Unit,
    onMoveDownClick: (LocalAccountId) -> Unit,
    onRenameClick: (LocalAccountId) -> Unit,
    onReloginClick: (LocalAccountId) -> Unit,
    onDeleteClick: (LocalAccountId) -> Unit,
) {
    // Disabled buttons keep an explicit outline: Material's disabled border would all but vanish.
    val actionButtonBorder = BorderStroke(1.dp, CodexMeterTheme.colors.border)
    Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm)) {
            OutlinedButton(
                onClick = { onMoveUpClick(account.id) },
                modifier = Modifier.weight(1f),
                enabled = account.canMoveUp,
                shape = CodexMeterShapes.md,
                border = actionButtonBorder,
            ) {
                Text(text = stringResource(R.string.account_move_up))
            }
            OutlinedButton(
                onClick = { onMoveDownClick(account.id) },
                modifier = Modifier.weight(1f),
                enabled = account.canMoveDown,
                shape = CodexMeterShapes.md,
                border = actionButtonBorder,
            ) {
                Text(text = stringResource(R.string.account_move_down))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm)) {
            OutlinedButton(
                onClick = { onRenameClick(account.id) },
                modifier = Modifier.weight(1f),
                shape = CodexMeterShapes.md,
                border = actionButtonBorder,
            ) {
                Text(text = stringResource(R.string.account_rename))
            }
            OutlinedButton(
                onClick = { onReloginClick(account.id) },
                modifier = Modifier.weight(1f),
                shape = CodexMeterShapes.md,
                border = actionButtonBorder,
            ) {
                Text(
                    text = stringResource(R.string.account_relogin),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(
            onClick = { onDeleteClick(account.id) },
            modifier = Modifier.fillMaxWidth(),
            shape = CodexMeterShapes.md,
            border = actionButtonBorder,
        ) {
            Text(
                text = stringResource(R.string.account_delete),
                color = CodexMeterTheme.colors.danger,
            )
        }
    }
}

@Composable
private fun StatusBadge(labelResId: Int, tone: AccountStatusTone) {
    Box(
        modifier = Modifier
            .clip(CodexMeterShapes.pill)
            .background(tone.softColor())
            .padding(horizontal = CodexMeterSpacing.md, vertical = CodexMeterSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(labelResId),
            style = MaterialTheme.typography.labelSmall,
            color = tone.color(),
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountCreditsUi.displayText(locale: Locale): String =
    when (this) {
        is AccountCreditsUi.Balance -> stringResource(
            R.string.account_credits_balance_format,
            formatCreditBalance(amount, locale),
        )
        AccountCreditsUi.Unlimited -> stringResource(R.string.account_credits_unlimited)
        AccountCreditsUi.Unavailable -> stringResource(R.string.account_credits_unavailable)
    }

private fun formatCreditBalance(amount: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(amount)

@Composable
@ReadOnlyComposable
private fun AccountStatusTone.color(): Color =
    when (this) {
        AccountStatusTone.Neutral -> CodexMeterTheme.colors.secondary
        AccountStatusTone.Success -> CodexMeterTheme.colors.success
        AccountStatusTone.Warning -> CodexMeterTheme.colors.warning
        AccountStatusTone.Danger -> CodexMeterTheme.colors.danger
    }

@Composable
@ReadOnlyComposable
private fun AccountStatusTone.softColor(): Color =
    when (this) {
        AccountStatusTone.Neutral -> CodexMeterTheme.colors.neutralAlt
        AccountStatusTone.Success -> CodexMeterTheme.colors.successSoft
        AccountStatusTone.Warning -> CodexMeterTheme.colors.warningSoft
        AccountStatusTone.Danger -> CodexMeterTheme.colors.dangerSoft
    }
