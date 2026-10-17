package com.kmnexus.codexmeter.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.auth.DeviceCodeLoginController
import com.kmnexus.codexmeter.domain.auth.DeviceCodeLoginNotifier
import com.kmnexus.codexmeter.domain.auth.NoopDeviceCodeLoginController
import com.kmnexus.codexmeter.domain.auth.NoopDeviceCodeLoginNotifier
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeConnect
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnect
import com.kmnexus.codexmeter.domain.account.AccountDeleteUseCase
import com.kmnexus.codexmeter.domain.account.AccountListUseCase
import com.kmnexus.codexmeter.domain.account.AccountRenameUseCase
import com.kmnexus.codexmeter.domain.account.NoopAccountRenameUseCase
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceStore
import com.kmnexus.codexmeter.domain.settings.NoopQuotaHistoryClearUseCase
import com.kmnexus.codexmeter.domain.settings.QuotaHistoryClearUseCase
import com.kmnexus.codexmeter.domain.settings.RetentionPreferenceStore
import com.kmnexus.codexmeter.ui.account.AccountQuotaAlertEvaluationRequester
import com.kmnexus.codexmeter.ui.account.AccountRefreshAllUseCase
import com.kmnexus.codexmeter.ui.account.AccountRoute
import com.kmnexus.codexmeter.ui.account.NoopAccountQuotaAlertEvaluationRequester
import com.kmnexus.codexmeter.ui.account.NoopAccountRefreshAllUseCase
import com.kmnexus.codexmeter.ui.auth.AddAccountEntryMode
import com.kmnexus.codexmeter.ui.auth.AddAccountRoute
import com.kmnexus.codexmeter.refresh.RefreshWorkScheduler
import com.kmnexus.codexmeter.ui.auth.ProviderSelectionScreen
import com.kmnexus.codexmeter.ui.auth.ProviderSelectionSheet
import com.kmnexus.codexmeter.ui.auth.ClaudeLoginRoute
import com.kmnexus.codexmeter.ui.auth.CodexLoginRoute
import com.kmnexus.codexmeter.ui.components.CodexMeterBackdrop
import com.kmnexus.codexmeter.data.currency.ExchangeRateReader
import com.kmnexus.codexmeter.domain.currency.CurrencyPreferenceReader
import com.kmnexus.codexmeter.domain.currency.CurrencyPreferences
import com.kmnexus.codexmeter.domain.theme.AppearancePreferenceStore
import com.kmnexus.codexmeter.ui.settings.NoopAppearancePreferenceStore
import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.account.InMemoryAccountDisplayPreferenceStore
import com.kmnexus.codexmeter.ui.home.HomeAccountsLoader
import com.kmnexus.codexmeter.ui.home.HomeRoute
import com.kmnexus.codexmeter.ui.motion.CodexMeterPageCascade
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import com.kmnexus.codexmeter.app.NotificationWindowChoicesLoader
import com.kmnexus.codexmeter.ui.settings.InMemoryNotificationPreferenceStore
import com.kmnexus.codexmeter.ui.settings.InMemoryRetentionPreferenceStore
import com.kmnexus.codexmeter.ui.settings.SettingsRoute
import com.kmnexus.codexmeter.ui.settings.SettingsDiagnosticsReader
import com.kmnexus.codexmeter.ui.settings.DefaultSettingsDiagnosticsReader

@Composable
fun CodexMeterNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = CodexMeterRoute.Home.route,
    // Per-provider device-code login controllers; lookup falls back to the Noop controller so
    // previews/tests stay valid with an empty map. The legacy Codex modes resolve the codex entry.
    deviceCodeLoginControllers: Map<ProviderId, DeviceCodeLoginController> = emptyMap(),
    deviceCodeLoginNotifier: DeviceCodeLoginNotifier = NoopDeviceCodeLoginNotifier,
    accountListUseCase: AccountListUseCase,
    accountDeleteUseCase: AccountDeleteUseCase,
    accountRenameUseCase: AccountRenameUseCase = NoopAccountRenameUseCase,
    accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester =
        NoopAccountQuotaAlertEvaluationRequester,
    accountRefreshAllUseCase: AccountRefreshAllUseCase = NoopAccountRefreshAllUseCase,
    homeAccountsLoader: HomeAccountsLoader,
    accountDisplayPreferenceStore: AccountDisplayPreferenceStore = InMemoryAccountDisplayPreferenceStore(),
    currencyPreferenceReader: CurrencyPreferenceReader = object : CurrencyPreferenceReader {
        override suspend fun currencyPreferences() = CurrencyPreferences()
    },
    exchangeRateReader: ExchangeRateReader = object : ExchangeRateReader {
        override suspend fun currentRates() = null
    },

    retentionPreferenceStore: RetentionPreferenceStore = InMemoryRetentionPreferenceStore(),
    notificationPreferenceStore: NotificationPreferenceStore = InMemoryNotificationPreferenceStore(),
    backgroundRefreshStatusReader: com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatusReader =
        com.kmnexus.codexmeter.ui.settings.InMemoryBackgroundRefreshStatusReader(),
    settingsDiagnosticsReader: SettingsDiagnosticsReader = DefaultSettingsDiagnosticsReader,
    quotaHistoryClearUseCase: QuotaHistoryClearUseCase = NoopQuotaHistoryClearUseCase,
    claudeConnect: ClaudeConnect? = null,
    codexBrowserConnect: CodexBrowserConnect? = null,
    notificationWindowChoicesLoader: NotificationWindowChoicesLoader =
        NotificationWindowChoicesLoader { _, _ -> emptyList() },
    appearancePreferenceStore: AppearancePreferenceStore = NoopAppearancePreferenceStore,
) {
    val tabs = CodexMeterRoute.bottomTabs
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: CodexMeterRoute.Home.route

    // The provider picker is a bottom sheet that unfolds from the tab bar; hoisted here so the sheet
    // and the tab bar are siblings in the root Box (the bar must paint on top of the sheet).
    var providerSheetVisible by remember { mutableStateOf(false) }

    fun navigateToAddAccount(entryMode: AddAccountEntryMode) {
        navController.navigate(CodexMeterRoute.AddAccount.routeFor(entryMode))
    }

    fun navigateToProviderSelection() {
        providerSheetVisible = true
    }

    fun navigateToTab(tab: CodexMeterRoute) {
        // Don't leave the picker hovering over a freshly switched tab.
        providerSheetVisible = false
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // After a successful add, land on the Account tab and clear the add-account flow off the stack.
    fun navigateToAccountAfterSave() {
        navController.navigate(CodexMeterRoute.Account.route) {
            popUpTo(CodexMeterRoute.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    // Both providers sign in through the browser, each on its own screen; Codex's screen also offers
    // its device-code sign-in as an alternative.
    fun navigateBasedOnAuthType(providerId: com.kmnexus.codexmeter.domain.model.ProviderId) {
        val nextMode = if (providerId == com.kmnexus.codexmeter.providers.ProviderRegistry.CLAUDE) {
            AddAccountEntryMode.ClaudeLogin()
        } else {
            AddAccountEntryMode.CodexBrowserLogin()
        }
        navigateToAddAccount(nextMode)
    }

    // Re-login goes straight to the account's own provider sign-in (no provider picker), carrying the
    // target account so the new credential rebinds in place instead of creating a duplicate.
    fun navigateToRelogin(
        providerId: com.kmnexus.codexmeter.domain.model.ProviderId,
        localAccountId: com.kmnexus.codexmeter.domain.model.LocalAccountId,
        providerAccountId: String?,
    ) {
        val nextMode = if (providerId == com.kmnexus.codexmeter.providers.ProviderRegistry.CLAUDE) {
            AddAccountEntryMode.ClaudeLogin(expectedProviderAccountId = providerAccountId)
        } else {
            AddAccountEntryMode.CodexBrowserLogin(expectedProviderAccountId = providerAccountId)
        }
        navigateToAddAccount(nextMode)
    }

    // The per-provider login flow is a full-screen modal; the tab bar only shows on the three tabs.
    val showBottomBar = tabs.any { it.route == currentRoute }

    Box(modifier = modifier.fillMaxSize()) {
        CodexMeterBackdrop(modifier = Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    CodexMeterBottomBar(
                        tabs = tabs,
                        currentRoute = currentRoute,
                        onTabSelected = { tab -> navigateToTab(tab) },
                    )
                }
            },
        ) { innerPadding ->
        @Composable
        fun AddAccountDestination(entryMode: AddAccountEntryMode) {
            when (entryMode) {
                is AddAccountEntryMode.ProviderSelection, AddAccountEntryMode.Choose -> {
                    CodexMeterDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        ProviderSelectionScreen(
                            onProviderSelected = { providerId ->
                                navigateBasedOnAuthType(providerId)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                is AddAccountEntryMode.LoginToCodex, is AddAccountEntryMode.CodexRelogin -> {
                    CodexMeterDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        AddAccountRoute(
                            entryMode = entryMode,
                            deviceCodeLoginController = deviceCodeLoginControllers[
                                com.kmnexus.codexmeter.providers.ProviderRegistry.CODEX,
                            ] ?: NoopDeviceCodeLoginController,
                            deviceCodeLoginNotifier = deviceCodeLoginNotifier,
                            onBackClick = { navController.popBackStack() },
                            onLoginSaved = { navigateToAccountAfterSave() },
                        )
                    }
                }
                is AddAccountEntryMode.CodexBrowserLogin -> {
                    CodexMeterDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val connector = codexBrowserConnect
                        if (connector != null) {
                            CodexLoginRoute(
                                connector = connector,
                                expectedProviderAccountId = entryMode.expectedProviderAccountId?.let(::ProviderAccountId),
                                onBack = { navController.popBackStack() },
                                onConnected = { navigateToAccountAfterSave() },
                                onUseDeviceCode = {
                                    val deviceCodeMode = entryMode.expectedProviderAccountId
                                        ?.let { AddAccountEntryMode.CodexRelogin(it) }
                                        ?: AddAccountEntryMode.LoginToCodex
                                    navController.popBackStack()
                                    navigateToAddAccount(deviceCodeMode)
                                },
                            )
                        } else {
                            StubAuthScreen(
                                title = "Codex",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
                is AddAccountEntryMode.ClaudeLogin -> {
                    CodexMeterDestination(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        val connector = claudeConnect
                        if (connector != null) {
                            ClaudeLoginRoute(
                                connector = connector,
                                expectedProviderAccountId = entryMode.expectedProviderAccountId?.let(::ProviderAccountId),
                                onBack = { navController.popBackStack() },
                                onConnected = { navigateToAccountAfterSave() },
                            )
                        } else {
                            StubAuthScreen(
                                title = "Claude",
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(CodexMeterRoute.Home.route) {
                CodexMeterDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    HomeRoute(
                        accountsLoader = homeAccountsLoader,
                        refreshAllUseCase = accountRefreshAllUseCase,
                        notificationPreferenceReader = notificationPreferenceStore,
                        onAddAccountClick = { navigateToProviderSelection() },
                        onOpenAccountsClick = { navigateToTab(CodexMeterRoute.Account) },
                        onReconnectClick = { card ->
                            navigateToRelogin(
                                ProviderId(card.providerId),
                                com.kmnexus.codexmeter.domain.model.LocalAccountId(card.localAccountId),
                                card.providerAccountId,
                            )
                        },
                    )
                }
            }
            composable(CodexMeterRoute.Account.route) {
                CodexMeterDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    AccountRoute(
                        accountListUseCase = accountListUseCase,
                        accountDeleteUseCase = accountDeleteUseCase,
                        accountRenameUseCase = accountRenameUseCase,
                        displayPreferenceStore = accountDisplayPreferenceStore,
                        notificationPreferenceStore = notificationPreferenceStore,
                        quotaAlertEvaluationRequester = accountQuotaAlertEvaluationRequester,
                        refreshAllUseCase = accountRefreshAllUseCase,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                        onLoginToCodexClick = { navigateToProviderSelection() },
                        onAddAccountClick = { navigateToProviderSelection() },
                        onReloginAccount = { providerId, localAccountId, providerAccountId ->
                            navigateToRelogin(providerId, localAccountId, providerAccountId)
                        },
                    )
                }
            }
            composable(CodexMeterRoute.Settings.route) {
                val settingsContext = LocalContext.current
                CodexMeterDestination(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    SettingsRoute(
                        accountListUseCase = accountListUseCase,
                        retentionPreferenceStore = retentionPreferenceStore,
                        notificationPreferenceStore = notificationPreferenceStore,
                        backgroundRefreshStatusReader = backgroundRefreshStatusReader,
                        diagnosticsReader = settingsDiagnosticsReader,
                        quotaHistoryClearUseCase = quotaHistoryClearUseCase,
                        backgroundRefreshScheduler = { minutes ->
                            RefreshWorkScheduler.from(settingsContext).applyIntervalMinutes(minutes)
                        },
                        notificationWindowChoicesLoader = notificationWindowChoicesLoader,
                        appearancePreferenceStore = appearancePreferenceStore,
                    )
                }
            }
            composable(CodexMeterRoute.AddAccount.route) {
                AddAccountDestination(entryMode = AddAccountEntryMode.Choose)
            }
            composable(
                route = CodexMeterRoute.AddAccount.routeWithEntryMode,
                arguments = listOf(
                    navArgument(CodexMeterRoute.AddAccount.ENTRY_MODE_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                AddAccountDestination(
                    entryMode = AddAccountEntryMode.fromRouteValue(
                        backStackEntry.arguments?.getString(CodexMeterRoute.AddAccount.ENTRY_MODE_ARG),
                    ),
                )
            }
        }
        }
        // Provider picker sheet — rendered LAST so it paints over the tab bar: the panel grows up
        // from the bottom edge and covers the bar while open. Dismisses before branching into the
        // per-provider full-screen login.
        ProviderSelectionSheet(
            visible = providerSheetVisible,
            onProviderSelected = { providerId ->
                providerSheetVisible = false
                navigateBasedOnAuthType(providerId)
            },
            onDismiss = { providerSheetVisible = false },
        )
    }
}

@Composable
private fun CodexMeterDestination(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CodexMeterBackdrop(modifier = Modifier.fillMaxSize())
        CodexMeterPageCascade(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Composable
private fun CodexMeterBottomBar(
    tabs: List<CodexMeterRoute>,
    currentRoute: String,
    onTabSelected: (CodexMeterRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CodexMeterTheme.colors.neutral),
    ) {
        HorizontalDivider(thickness = 1.dp, color = CodexMeterTheme.colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                CodexMeterBottomBarItem(
                    tab = tab,
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            onTabSelected(tab)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.CodexMeterBottomBarItem(
    tab: CodexMeterRoute,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) CodexMeterTheme.colors.primary else CodexMeterTheme.colors.secondary
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tab.iconResId),
            // The label below names the tab; the icon would only repeat it.
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(tab.labelResId),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}
