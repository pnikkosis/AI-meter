package com.kmnexus.codexmeter.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.ui.account.AccountRefreshAllUseCase
import com.kmnexus.codexmeter.ui.components.AppCard
import com.kmnexus.codexmeter.ui.components.AppPullToRefreshIndicator
import com.kmnexus.codexmeter.ui.components.DashedAddButton
import com.kmnexus.codexmeter.ui.components.ProviderChip
import com.kmnexus.codexmeter.ui.motion.AnimatedQuotaPercent
import com.kmnexus.codexmeter.ui.motion.MotionQuotaRail
import com.kmnexus.codexmeter.ui.motion.rememberCodexMeterAnimatorsEnabled
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelText
import com.kmnexus.codexmeter.ui.quota.shortMomentFormatter
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import com.kmnexus.codexmeter.ui.theme.CodexMeterTypography
import java.time.Instant

@Composable
fun HomeRoute(
    accountsLoader: HomeAccountsLoader,
    refreshAllUseCase: AccountRefreshAllUseCase,
    notificationPreferenceReader: NotificationPreferenceReader,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            accountsLoader = accountsLoader,
            refreshAllUseCase = refreshAllUseCase,
            notificationPreferenceReader = notificationPreferenceReader,
        ),
    ),
    onAddAccountClick: () -> Unit = {},
    onOpenAccountsClick: () -> Unit = {},
    onReconnectClick: (HomeAccountCardUi) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HomeScreen(
        uiState = uiState,
        modifier = modifier,
        onRefreshClick = viewModel::refreshAll,
        onAddAccountClick = onAddAccountClick,
        onOpenAccountsClick = onOpenAccountsClick,
        onReconnectClick = onReconnectClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    onRefreshClick: () -> Unit = {},
    onAddAccountClick: () -> Unit = {},
    onOpenAccountsClick: () -> Unit = {},
    onReconnectClick: (HomeAccountCardUi) -> Unit = {},
) {
    val pullState = rememberPullToRefreshState()
    val moment = shortMomentFormatter()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefreshClick,
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
            HomeHeader(uiState = uiState, moment = moment, onRefreshClick = onRefreshClick)
            when {
                uiState.isLoading -> Unit
                uiState.accountCount == 0 -> HomeEmptyCard(onAddAccountClick = onAddAccountClick)
                uiState.accounts.isEmpty() -> HomeAllHiddenCard(onOpenAccountsClick = onOpenAccountsClick)
                else -> uiState.accounts.forEach { card ->
                    HomeAccountCard(card = card, moment = moment, onReconnectClick = onReconnectClick)
                }
            }
            if (!uiState.isLoading && uiState.accounts.isNotEmpty() && uiState.hiddenAccountCount > 0) {
                HiddenAccountsLine(count = uiState.hiddenAccountCount, onOpenAccountsClick = onOpenAccountsClick)
            }
            if (!uiState.isLoading && uiState.accountCount > 0) {
                DashedAddButton(text = stringResource(R.string.home_add_account), onClick = onAddAccountClick)
            }
            Spacer(modifier = Modifier.height(CodexMeterSpacing.bottomNavigationClearance))
        }
    }
}

@Composable
private fun HomeHeader(
    uiState: HomeUiState,
    moment: (Instant) -> String,
    onRefreshClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexMeterSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = CodexMeterTypography.display,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (uiState.accountCount > 0) {
                val count = pluralStringResource(R.plurals.home_account_count, uiState.accountCount, uiState.accountCount)
                val lastUpdatedAt = uiState.lastUpdatedAt
                Text(
                    text = when {
                        uiState.isRefreshing -> stringResource(R.string.home_summary_refreshing_format, count)
                        lastUpdatedAt != null -> stringResource(R.string.home_summary_updated_format, count, moment(lastUpdatedAt))
                        else -> count
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (uiState.accountCount > 0) {
            HomeRefreshButton(isRefreshing = uiState.isRefreshing, onRefreshClick = onRefreshClick)
        }
    }
}

@Composable
private fun HomeRefreshButton(isRefreshing: Boolean, onRefreshClick: () -> Unit) {
    val animatorsEnabled = rememberCodexMeterAnimatorsEnabled()
    val rotation = if (isRefreshing && animatorsEnabled) {
        val transition = rememberInfiniteTransition(label = "home_refresh_icon")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900, easing = LinearEasing)),
            label = "home_refresh_icon_rotation",
        )
        angle
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CodexMeterTheme.colors.surface)
            .border(1.dp, CodexMeterTheme.colors.border, CircleShape)
            .clickable(enabled = !isRefreshing, role = Role.Button, onClick = onRefreshClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_action_refresh),
            contentDescription = stringResource(R.string.home_refresh_all),
            tint = CodexMeterTheme.colors.primary,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun HomeAccountCard(
    card: HomeAccountCardUi,
    moment: (Instant) -> String,
    onReconnectClick: (HomeAccountCardUi) -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProviderChip(name = card.providerName)
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            card.planName?.let { plan ->
                Text(
                    text = plan,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        when (card.windows.size) {
            0 -> Unit
            1 -> SingleWindow(window = card.windows.single(), moment = moment)
            else -> WindowGrid(windows = card.windows, moment = moment)
        }
        CardStatusFooter(card = card, moment = moment, onReconnectClick = onReconnectClick)
    }
}

@Composable
private fun SingleWindow(window: HomeWindowUi, moment: (Instant) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = quotaWindowLabelText(window.labelResId, window.labelName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AnimatedQuotaPercent(
                    percent = window.percentLeft,
                    style = CodexMeterTypography.number,
                    color = window.level.numberColor(),
                )
                if (window.percentLeft != null) {
                    Text(
                        text = stringResource(R.string.home_window_available),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
        MotionQuotaRail(percent = window.percentLeft, color = window.level.barColor())
        WindowDetailLine(window = window, moment = moment)
    }
}

@Composable
private fun WindowGrid(windows: List<HomeWindowUi>, moment: (Instant) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        windows.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { window ->
                    WindowCell(window = window, moment = moment, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WindowCell(window: HomeWindowUi, moment: (Instant) -> String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = quotaWindowLabelText(window.labelResId, window.labelName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AnimatedQuotaPercent(
            percent = window.percentLeft,
            style = CodexMeterTypography.number.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = window.level.numberColor(),
        )
        MotionQuotaRail(percent = window.percentLeft, color = window.level.barColor())
        WindowDetailLine(window = window, moment = moment)
    }
}

/** Under each bar: when it resets, said with the level when it matters ("Agotado · vuelve jue 06:21"). */
@Composable
private fun WindowDetailLine(window: HomeWindowUi, moment: (Instant) -> String) {
    val resetText = window.resetAt?.let(moment)
    val text = when {
        window.level == QuotaLevel.Unknown -> stringResource(R.string.home_window_no_figure)
        window.level == QuotaLevel.Depleted ->
            resetText?.let { stringResource(R.string.home_window_depleted_format, it) }
                ?: stringResource(R.string.home_window_depleted)
        window.valueText != null -> window.valueText
        window.level == QuotaLevel.Warning ->
            resetText?.let { stringResource(R.string.home_window_low_format, it) }
                ?: stringResource(R.string.home_window_low)
        resetText != null -> stringResource(R.string.home_reset_at_format, resetText)
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = when (window.level) {
            QuotaLevel.Depleted -> CodexMeterTheme.colors.danger
            QuotaLevel.Warning -> CodexMeterTheme.colors.warning
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardStatusFooter(
    card: HomeAccountCardUi,
    moment: (Instant) -> String,
    onReconnectClick: (HomeAccountCardUi) -> Unit,
) {
    val updated = card.lastUpdatedAt?.let(moment)
    when (card.status) {
        HomeAccountStatus.Fresh -> Unit
        HomeAccountStatus.Stale -> updated?.let {
            FooterText(stringResource(R.string.home_status_stale_format, it), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HomeAccountStatus.Expired -> updated?.let {
            FooterText(stringResource(R.string.home_status_expired_format, it), CodexMeterTheme.colors.warning)
        }
        HomeAccountStatus.RefreshFailed -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            card.errorMessageResId?.let { FooterText(stringResource(it), CodexMeterTheme.colors.warning) }
            updated?.let {
                FooterText(stringResource(R.string.home_status_stale_format, it), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HomeAccountStatus.NeedsSignIn -> Column(verticalArrangement = Arrangement.spacedBy(CodexMeterSpacing.sm)) {
            FooterText(stringResource(R.string.home_status_needs_sign_in), CodexMeterTheme.colors.danger)
            OutlinedButton(
                onClick = { onReconnectClick(card) },
                shape = CodexMeterShapes.md,
            ) {
                Text(text = stringResource(R.string.home_reconnect))
            }
        }
        HomeAccountStatus.NoData ->
            FooterText(stringResource(R.string.home_status_no_data), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FooterText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun HomeEmptyCard(onAddAccountClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAddAccountClick,
            shape = CodexMeterShapes.md,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(CodexMeterSpacing.sm))
            Text(text = stringResource(R.string.home_add_account))
        }
    }
}

@Composable
private fun HomeAllHiddenCard(onOpenAccountsClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_all_hidden_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpenAccountsClick, shape = CodexMeterShapes.md) {
            Text(text = stringResource(R.string.home_open_accounts))
        }
    }
}

@Composable
private fun HiddenAccountsLine(count: Int, onOpenAccountsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.home_hidden_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenAccountsClick) {
            Text(text = stringResource(R.string.home_open_accounts))
        }
    }
}

@Composable
private fun QuotaLevel.numberColor(): Color =
    when (this) {
        QuotaLevel.Depleted -> CodexMeterTheme.colors.danger
        QuotaLevel.Warning -> CodexMeterTheme.colors.warning
        QuotaLevel.Unknown -> CodexMeterTheme.colors.tertiary
        QuotaLevel.Normal,
        QuotaLevel.Caution -> MaterialTheme.colorScheme.onSurface
    }

@Composable
private fun QuotaLevel.barColor(): Color =
    when (this) {
        QuotaLevel.Normal -> CodexMeterTheme.colors.accent
        QuotaLevel.Caution,
        QuotaLevel.Warning -> CodexMeterTheme.colors.warning
        QuotaLevel.Depleted -> CodexMeterTheme.colors.danger
        QuotaLevel.Unknown -> CodexMeterTheme.colors.track
    }
