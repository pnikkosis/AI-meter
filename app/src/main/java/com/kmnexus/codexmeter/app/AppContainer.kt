package com.kmnexus.codexmeter.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.core.network.ProviderHosts
import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.data.local.db.CodexMeterDatabase
import com.kmnexus.codexmeter.data.preferences.CurrentAccountPreferences
import com.kmnexus.codexmeter.data.preferences.CurrentAccountReader
import com.kmnexus.codexmeter.data.preferences.NotificationPreferencesDataStore
import com.kmnexus.codexmeter.data.preferences.PrimaryQuotaWindowPreferences
import com.kmnexus.codexmeter.data.preferences.RetentionPreferences
import com.kmnexus.codexmeter.data.repository.AccountDeletionRepository
import com.kmnexus.codexmeter.data.repository.AccountListRepository
import com.kmnexus.codexmeter.data.repository.AccountMutationRepository
import com.kmnexus.codexmeter.data.repository.QuotaHistoryClearRepository
import com.kmnexus.codexmeter.data.repository.RetentionCleanupRepository
import com.kmnexus.codexmeter.data.repository.RoomNotificationAlertStateStore
import com.kmnexus.codexmeter.data.repository.RoomCodexSessionImportPersistence
import com.kmnexus.codexmeter.data.repository.RoomQuotaSnapshotStore
import com.kmnexus.codexmeter.data.repository.RoomRefreshAccountStatusStore
import com.kmnexus.codexmeter.data.repository.RoomRefreshAttemptStore
import com.kmnexus.codexmeter.data.secure.AesGcmPayloadCipher
import com.kmnexus.codexmeter.data.secure.AndroidKeystoreSecretKeyProvider
import com.kmnexus.codexmeter.data.secure.FileSecureSessionStore
import com.kmnexus.codexmeter.data.secure.PayloadCipher
import com.kmnexus.codexmeter.data.secure.ProviderSessionRevoker
import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.domain.account.AccountDeleteUseCase
import com.kmnexus.codexmeter.domain.account.AccountListUseCase
import com.kmnexus.codexmeter.domain.account.AccountRenameUseCase
import com.kmnexus.codexmeter.domain.account.AccountSwitchUseCase
import com.kmnexus.codexmeter.domain.account.AccountSwitchRefreshRequester
import com.kmnexus.codexmeter.domain.auth.DeviceCodeLoginController
import com.kmnexus.codexmeter.domain.auth.DeviceCodeLoginDiagnosticsReader
import com.kmnexus.codexmeter.domain.auth.DeviceCodeLoginNotifier
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.RefreshAttemptId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStateFactory
import com.kmnexus.codexmeter.domain.refresh.RefreshTrigger
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceStore
import com.kmnexus.codexmeter.domain.settings.PrimaryQuotaWindowPreferenceStore
import com.kmnexus.codexmeter.domain.settings.QuotaHistoryClearUseCase
import com.kmnexus.codexmeter.notification.AndroidNotificationRequestOptionsReader
import com.kmnexus.codexmeter.notification.AndroidDeviceCodeLoginNotifier
import com.kmnexus.codexmeter.notification.AndroidNotificationSink
import com.kmnexus.codexmeter.notification.AccountErrorEventReader
import com.kmnexus.codexmeter.notification.AccountErrorPolicy
import com.kmnexus.codexmeter.notification.CurrentQuotaNotificationPublisher
import com.kmnexus.codexmeter.notification.NotificationPreferenceAlertThresholdsReader
import com.kmnexus.codexmeter.notification.NotificationPreferenceQuotaAlertWindowReader
import com.kmnexus.codexmeter.providers.codex.CodexRefreshProvider
import com.kmnexus.codexmeter.providers.codex.CodexSessionCipher
import com.kmnexus.codexmeter.providers.codex.CodexTokenRefresh
import com.kmnexus.codexmeter.providers.codex.CodexUsageFetcher
import com.kmnexus.codexmeter.providers.codex.auth.CodexSessionImporter
import com.kmnexus.codexmeter.providers.codex.auth.CodexDeviceCodeClient
import com.kmnexus.codexmeter.providers.codex.auth.CodexDeviceCodeLoginController
import com.kmnexus.codexmeter.providers.codex.auth.CodexDeviceCodeLoginUseCase
import com.kmnexus.codexmeter.providers.codex.auth.CodexOAuthTokenExchanger
import com.kmnexus.codexmeter.providers.codex.auth.CodexTokenRefresher
import com.kmnexus.codexmeter.providers.codex.auth.CodexTokenRevoker
import com.kmnexus.codexmeter.providers.codex.auth.DeviceCodeChallenge
import com.kmnexus.codexmeter.providers.codex.auth.DeviceCodeLoginAttemptId
import com.kmnexus.codexmeter.providers.codex.mapper.CodexUsageMapper
import com.kmnexus.codexmeter.providers.codex.network.CodexUsageClient
import com.kmnexus.codexmeter.providers.codex.session.AndroidKeystoreCodexSessionCipher
import com.kmnexus.codexmeter.providers.codex.session.CodexSessionPayload
import com.kmnexus.codexmeter.providers.claude.ClaudeRefreshProvider
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeAccountConnector
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeConnect
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeTokenClient
import com.kmnexus.codexmeter.providers.claude.network.ClaudeUsageClient
import com.kmnexus.codexmeter.data.currency.ExchangeRateRepository
import com.kmnexus.codexmeter.data.preferences.CurrencyPreferencesDataStore
import com.kmnexus.codexmeter.refresh.AttemptIdProvider
import com.kmnexus.codexmeter.refresh.CompositeCurrentQuotaStatePublisher
import com.kmnexus.codexmeter.refresh.CompositeRefreshProvider
import com.kmnexus.codexmeter.refresh.CurrentQuotaStatePublisher
import com.kmnexus.codexmeter.refresh.ExchangeRateRefresher
import com.kmnexus.codexmeter.refresh.MultiAccountRefreshRunner
import com.kmnexus.codexmeter.refresh.RefreshCoordinator
import com.kmnexus.codexmeter.ui.account.AccountRefreshAllUseCase
import com.kmnexus.codexmeter.refresh.RefreshProvider
import com.kmnexus.codexmeter.ui.account.AccountQuotaAlertEvaluationRequester
import com.kmnexus.codexmeter.ui.home.HomeCurrentQuotaStateLoader
import com.kmnexus.codexmeter.ui.home.HomeRefreshUseCase
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatusReader
import com.kmnexus.codexmeter.ui.settings.SettingsDiagnosticsReader
import com.kmnexus.codexmeter.widget.WidgetDeletedAccountStateCleaner
import com.kmnexus.codexmeter.widget.WidgetQuotaStateUpdater
import com.kmnexus.codexmeter.widget.WidgetQuotaStateLoader
import com.kmnexus.codexmeter.ui.home.HomeTrendHistoryLoader
import java.time.Clock
import java.time.Instant
import java.util.UUID
import com.kmnexus.codexmeter.data.preferences.AppearancePreferences
import com.kmnexus.codexmeter.domain.theme.AppearancePreferenceStore
import com.kmnexus.codexmeter.domain.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer private constructor(
    val refreshCoordinator: RefreshCoordinator,
    val accountListUseCase: AccountListUseCase,
    val accountDeleteUseCase: AccountDeleteUseCase,
    val accountSwitchUseCase: AccountSwitchUseCase,
    val accountRenameUseCase: AccountRenameUseCase,
    val accountQuotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester,
    val homeCurrentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    val homeRefreshUseCase: HomeRefreshUseCase,
    val deviceCodeLoginController: DeviceCodeLoginController,
    /** Per-provider device-code login controllers; the legacy single property is the codex entry. */
    val deviceCodeLoginControllers: Map<ProviderId, DeviceCodeLoginController>,
    val deviceCodeLoginNotifier: DeviceCodeLoginNotifier,
    val retentionPreferences: RetentionPreferences,
    val notificationPreferences: NotificationPreferenceStore,
    val primaryQuotaWindowPreferences: PrimaryQuotaWindowPreferenceStore,
    val quotaHistoryClearUseCase: QuotaHistoryClearUseCase,
    val homeTrendHistoryLoader: HomeTrendHistoryLoader,
    val backgroundRefreshStatusReader: SettingsBackgroundRefreshStatusReader,
    val settingsDiagnosticsReader: SettingsDiagnosticsReader,
    val widgetQuotaStateLoader: WidgetQuotaStateLoader,
    val startupMaintenance: StartupMaintenance,
    val claudeConnect: ClaudeConnect,
    val appearancePreferences: AppearancePreferenceStore,
    val initialThemeMode: ThemeMode,
    val currencyPreferences: CurrencyPreferencesDataStore,
    val exchangeRateRepository: ExchangeRateRepository,
    private val currentAccountStore: CurrentQuotaRefreshAccountStore,
    val exchangeRateRefresher: ExchangeRateRefresher,
    val notificationWindowChoicesLoader: NotificationWindowChoicesLoader,
) {
    suspend fun activeQuotaRefreshAccounts(): List<ProviderAccount> =
        currentAccountStore.activeAccounts()

    /**
     * Pull-to-refresh on the Account screen: refresh every manually-refreshable account in parallel,
     * including NeedsReauth ones, so a transient failure can be retried and self-heal on success.
     */
    val accountRefreshAllUseCase: AccountRefreshAllUseCase
        get() = AccountRefreshAllUseCase {
            MultiAccountRefreshRunner(
                refreshCoordinator = refreshCoordinator,
                exchangeRateRefresher = exchangeRateRefresher,
            ).refresh(
                accounts = currentAccountStore.manuallyRefreshableAccounts(),
                trigger = RefreshTrigger.Manual,
            )
        }

    companion object {
        fun create(context: Context): AppContainer {
            val appContext = context.applicationContext
            val database = Room.databaseBuilder(
                appContext,
                CodexMeterDatabase::class.java,
                CodexMeterDatabase.DATABASE_NAME,
            ).build()
            val currentAccountPreferences = CurrentAccountPreferences.create(
                file = appContext.preferencesDataStoreFile(PREFERENCES_FILE_NAME),
            )
            val appearancePreferences = AppearancePreferences.create(
                file = appContext.preferencesDataStoreFile(APPEARANCE_PREFERENCES_FILE_NAME),
            )
            // Preheat the first value synchronously so the first frame uses the correct theme.
            val initialThemeMode = runBlocking {
                appearancePreferences.themeMode.first()
            }
            val retentionPreferences = RetentionPreferences(currentAccountPreferences.dataStore)
            val notificationPreferences = NotificationPreferencesDataStore(currentAccountPreferences.dataStore)
            val primaryQuotaWindowPreferences = PrimaryQuotaWindowPreferences(currentAccountPreferences.dataStore)
            // Every provider request goes over HTTPS to an allowlisted provider host, without
            // following redirects. Token grants use a client that never replays a request, so a
            // rotated refresh token cannot be burned by a silent transport retry.
            val httpClient = ProviderHttpClient(allowedHosts = ProviderHosts.ALL)
            val tokenHttpClient = ProviderHttpClient.noRetry(allowedHosts = ProviderHosts.ALL)
            // Disconnecting waits at most this long for the provider before deleting local data.
            val revokeHttpClient = ProviderHttpClient(
                okHttpClient = OkHttpClient.Builder()
                    .callTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build(),
                allowedHosts = ProviderHosts.ALL,
            )
            val usageClient = CodexUsageClient(httpClient)
            val sessionStore = FileSecureSessionStore(
                directory = appContext.filesDir.resolve(SESSION_DIRECTORY_NAME),
            )
            val sessionCipher = AndroidKeystoreCodexSessionCipher()
            val payloadCipher: PayloadCipher = AesGcmPayloadCipher(AndroidKeystoreSecretKeyProvider())
            val widgetDeletedAccountStateCleaner = WidgetDeletedAccountStateCleaner(appContext)
            val clock = Clock.systemUTC()
            val currencyPreferences = CurrencyPreferencesDataStore(currentAccountPreferences.dataStore)
            // Currency conversion is disabled: Codex and Claude report percentages, not balances, and
            // the app makes no network calls besides the providers' own APIs. The repository stays
            // as an empty cache so the balance display code paths remain inert.
            val exchangeRateRepository = ExchangeRateRepository(
                cache = currencyPreferences,
                fetch = { null },
            )
            val defaultAccountDisplayName = appContext.getString(R.string.account_default_display_name)
            val currentQuotaStateFactory = CurrentQuotaStateFactory()
            val widgetQuotaStateRepository = WidgetQuotaStateRepository(
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStateFactory = currentQuotaStateFactory,
                notificationPreferenceReader = notificationPreferences,
                currencyPreferenceReader = currencyPreferences,
                exchangeRateReader = exchangeRateRepository,
                clock = clock,
            )
            val currentQuotaStateRepository = CurrentQuotaStateRepository(
                currentAccountReader = currentAccountPreferences,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStateFactory = currentQuotaStateFactory,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                notificationPreferenceReader = notificationPreferences,
                clock = clock,
                currencyPreferenceReader = currencyPreferences,
                exchangeRateReader = exchangeRateRepository,
            )
            val accountErrorPolicy = AccountErrorPolicy()

            val codexRefreshProvider = CodexRefreshProvider(
                sessionStore = sessionStore,
                sessionCipher = sessionCipher,
                tokenRefresh = CodexTokenRefresh(CodexTokenRefresher(tokenHttpClient)::refresh),
                usageFetcher = CodexUsageFetcher(usageClient::fetchUsage),
                clock = clock,
            )
            val claudeUsageClient = ClaudeUsageClient(httpClient)
            val claudeTokenClient = ClaudeTokenClient(tokenHttpClient)
            val claudeRefreshProvider = ClaudeRefreshProvider(
                usageClient = claudeUsageClient,
                tokenClient = claudeTokenClient,
                sessionStore = sessionStore,
                payloadCipher = payloadCipher,
                clock = clock,
            )
            val compositeRefreshProvider = CompositeRefreshProvider(
                providers = mapOf(
                    ProviderId("codex") to codexRefreshProvider,
                    ProviderId("claude") to claudeRefreshProvider,
                ),
            )
            return fromDatabase(
                appContext = appContext,
                database = database,
                currentAccountReader = currentAccountPreferences,
                currentAccountPreferences = currentAccountPreferences,
                retentionPreferences = retentionPreferences,
                notificationPreferences = notificationPreferences,
                primaryQuotaWindowPreferences = primaryQuotaWindowPreferences,
                sessionStore = sessionStore,
                sessionCipher = sessionCipher,
                refreshProvider = compositeRefreshProvider,
                usageClient = usageClient,
                claudeUsageClient = claudeUsageClient,
                claudeTokenClient = claudeTokenClient,
                payloadCipher = payloadCipher,
                deviceCodeLoginNotifier = AndroidDeviceCodeLoginNotifier(
                    notificationSink = AndroidNotificationSink(appContext),
                ),
                currentQuotaStatePublisher = CompositeCurrentQuotaStatePublisher(
                    listOf(
                        WidgetQuotaStateUpdater(
                            context = appContext,
                            notificationPreferenceReader = notificationPreferences,
                            widgetQuotaStateLoader = widgetQuotaStateRepository,
                        ),
                        CurrentQuotaNotificationPublisher(
                            notificationSink = AndroidNotificationSink(appContext),
                            alertStateStore = RoomNotificationAlertStateStore(database.alertStateDao()),
                            optionsReader = AndroidNotificationRequestOptionsReader(
                                context = appContext,
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            alertThresholdsReader = NotificationPreferenceAlertThresholdsReader(
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            alertWindowPreferenceReader = NotificationPreferenceQuotaAlertWindowReader(
                                notificationPreferenceReader = notificationPreferences,
                            ),
                            statusNotificationStateLoader = currentQuotaStateRepository,
                            accountErrorEventReader = AccountErrorEventReader { state ->
                                val account = state.account
                                val consecutiveFailureCount = if (account == null) {
                                    0
                                } else {
                                    database.refreshAttemptDao().countConsecutiveFailuresSinceLatestSuccess(
                                        providerId = account.providerId.value,
                                        localAccountId = account.localAccountId.value,
                                    )
                                }
                                accountErrorPolicy.evaluate(
                                    state = state,
                                    consecutiveFailureCount = consecutiveFailureCount,
                                )
                            },
                            clock = clock,
                            currencyPreferenceReader = currencyPreferences,
                            exchangeRateReader = exchangeRateRepository,
                        ),
                    ),
                ),
                currentQuotaStateRepository = currentQuotaStateRepository,
                accountListRepository = AccountListRepository(
                    providerAccountDao = database.providerAccountDao(),
                    quotaSnapshotDao = database.quotaSnapshotDao(),
                    currentAccountReader = currentAccountPreferences,
                ),
                accountDeletionRepository = AccountDeletionRepository(
                    database = database,
                    secureSessionStore = sessionStore,
                    currentAccountStore = currentAccountPreferences,
                    deletedAccountStateCleaner = widgetDeletedAccountStateCleaner,
                    sessionRevoker = ProviderSessionRevoker(
                        mapOf(ProviderId("codex") to CodexTokenRevoker(revokeHttpClient, sessionCipher)),
                    ),
                ),
                retentionCleanupRepository = RetentionCleanupRepository(database),
                quotaHistoryClearRepository = QuotaHistoryClearRepository(
                    database = database,
                    currentAccountReader = currentAccountPreferences,
                ),
                currentQuotaStateFactory = currentQuotaStateFactory,
                widgetQuotaStateRepository = widgetQuotaStateRepository,
                defaultAccountDisplayName = defaultAccountDisplayName,
                httpClient = httpClient,
                clock = clock,
                appearancePreferences = appearancePreferences,
                initialThemeMode = initialThemeMode,
                currencyPreferences = currencyPreferences,
                exchangeRateRepository = exchangeRateRepository,
                exchangeRateRefresher = ExchangeRateRefresher { },
            )
        }

        private fun fromDatabase(
            appContext: Context,
            database: CodexMeterDatabase,
            currentAccountReader: CurrentAccountReader,
            currentAccountPreferences: CurrentAccountPreferences,
            retentionPreferences: RetentionPreferences,
            notificationPreferences: NotificationPreferencesDataStore,
            primaryQuotaWindowPreferences: PrimaryQuotaWindowPreferences,
            sessionStore: FileSecureSessionStore,
            sessionCipher: CodexSessionCipher,
            refreshProvider: RefreshProvider,
            usageClient: CodexUsageClient,
            claudeUsageClient: ClaudeUsageClient,
            claudeTokenClient: ClaudeTokenClient,
            payloadCipher: PayloadCipher,
            deviceCodeLoginNotifier: DeviceCodeLoginNotifier,
            currentQuotaStatePublisher: CurrentQuotaStatePublisher,
            currentQuotaStateRepository: CurrentQuotaStateRepository,
            accountListRepository: AccountListRepository,
            accountDeletionRepository: AccountDeletionRepository,
            retentionCleanupRepository: RetentionCleanupRepository,
            quotaHistoryClearRepository: QuotaHistoryClearRepository,
            currentQuotaStateFactory: CurrentQuotaStateFactory,
            widgetQuotaStateRepository: WidgetQuotaStateRepository,
            defaultAccountDisplayName: String,
            httpClient: ProviderHttpClient,
            clock: Clock,
            appearancePreferences: AppearancePreferenceStore,
            initialThemeMode: ThemeMode,
            currencyPreferences: CurrencyPreferencesDataStore,
            exchangeRateRepository: ExchangeRateRepository,
            exchangeRateRefresher: ExchangeRateRefresher,
        ): AppContainer {
            // Shared by both providers: commits account, first snapshot and encrypted session together
            // and rolls back the session file and current-account choice if the database write fails.
            val importPersistence = RoomCodexSessionImportPersistence(
                database = database,
                sessionStore = sessionStore,
                currentAccountStore = currentAccountPreferences,
            )
            val sessionImporter = CodexSessionImporter(
                usageClient = CodexSessionImporter.UsageClient(usageClient::fetchUsage),
                mapper = CodexUsageMapper(),
                importPersistence = importPersistence,
                sessionEnvelopeFactory = CodexSessionEnvelopeFactory(sessionCipher),
                localAccountIdProvider = CodexSessionImporter.LocalAccountIdProvider {
                    LocalAccountId("codex-${UUID.randomUUID()}")
                },
                defaultDisplayName = defaultAccountDisplayName,
                clock = clock,
            )
            val claudeAccountConnector = ClaudeAccountConnector(
                tokenClient = claudeTokenClient,
                usageClient = claudeUsageClient,
                payloadCipher = payloadCipher,
                persistence = importPersistence,
                clock = clock,
            )
            val codexDeviceCodeClient = CodexDeviceCodeClient(httpClient)
            val codexDeviceCodeLoginUseCase = CodexDeviceCodeLoginUseCase(
                deviceCodeClient = object : CodexDeviceCodeLoginUseCase.DeviceCodeClient {
                    override suspend fun requestDeviceCode() =
                        codexDeviceCodeClient.requestDeviceCode()

                    override suspend fun pollAuthorization(challenge: DeviceCodeChallenge) =
                        codexDeviceCodeClient.pollAuthorization(challenge)
                },
                tokenExchanger = CodexDeviceCodeLoginUseCase.TokenExchanger(
                    CodexOAuthTokenExchanger(httpClient)::exchange,
                ),
                sessionImporter = object : CodexDeviceCodeLoginUseCase.SessionImporter {
                    override suspend fun prepareDeviceCodeSession(
                        session: CodexSessionPayload,
                    ): CodexSessionImporter.PrepareResult =
                        sessionImporter.prepareDeviceCodeSession(session)

                    override suspend fun commitPreparedDeviceCodeSession(
                        preparedImport: CodexSessionImporter.PreparedImport,
                    ): CodexSessionImporter.Result =
                        sessionImporter.commitPreparedDeviceCodeSession(preparedImport)
                },
                attemptIdProvider = {
                    DeviceCodeLoginAttemptId("device-${UUID.randomUUID()}")
                },
                clock = clock,
            )
            val currentQuotaRefreshAccountStore = CurrentQuotaRefreshAccountStore(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
            )
            val refreshCoordinator = RefreshCoordinator(
                provider = refreshProvider,
                snapshotStore = RoomQuotaSnapshotStore(database.quotaSnapshotDao()),
                attemptStore = RoomRefreshAttemptStore(database.refreshAttemptDao()),
                accountStatusStore = RoomRefreshAccountStatusStore(database.providerAccountDao()),
                attemptIdProvider = AttemptIdProvider {
                    RefreshAttemptId("periodic-${UUID.randomUUID()}")
                },
                currentQuotaStatePublisher = currentQuotaStatePublisher,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                clock = clock,
            )
            val currentAccountStateRepublisher = CurrentAccountQuotaStateRepublisher(
                currentAccountReader = currentAccountPreferences,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                currentQuotaStatePublisher = currentQuotaStatePublisher,
                currentQuotaStateFactory = currentQuotaStateFactory,
                primaryQuotaWindowPreferenceReader = primaryQuotaWindowPreferences,
                clock = clock,
            )
            val accountMutationRepository = AccountMutationRepository(
                providerAccountDao = database.providerAccountDao(),
                currentAccountStore = currentAccountPreferences,
                accountSwitchRefreshRequester = AccountSwitchRefreshRequester { account ->
                    try {
                        refreshCoordinator.refresh(
                            account = account,
                            trigger = RefreshTrigger.AccountSwitch,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        // Account selection is already durable; a later refresh/app open can recover state.
                    }
                },
                currentAccountStateRepublisher = currentAccountStateRepublisher,
                clock = clock,
            )
            val homeRefreshUseCase = HomeRefreshCoordinatorUseCase(
                currentAccountStore = currentQuotaRefreshAccountStore,
                refreshCoordinator = refreshCoordinator,
                currentQuotaStateLoader = currentQuotaStateRepository,
            )
            val homeTrendHistoryRepository = HomeTrendHistoryRepository(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                clock = clock,
            )
            val backgroundRefreshStatusRepository = SettingsBackgroundRefreshStatusRepository(
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
            )
            val deviceCodeLoginController = CodexDeviceCodeLoginController(
                useCase = codexDeviceCodeLoginUseCase,
                onSaved = { saved ->
                    currentQuotaStatePublisher.publish(
                        currentQuotaStateFactory.create(
                            account = saved.account,
                            latestSnapshot = saved.snapshot,
                            latestAttempt = null,
                            now = clock.instant(),
                            primaryWindowId = primaryQuotaWindowPreferences.primaryQuotaWindowId(),
                        ),
                    )
                },
            )
            val deviceCodeLoginDiagnosticsReader = deviceCodeLoginController as DeviceCodeLoginDiagnosticsReader
            val settingsDiagnosticsReader = SettingsDiagnosticsRepository(
                context = appContext,
                currentAccountReader = currentAccountReader,
                providerAccountDao = database.providerAccountDao(),
                quotaSnapshotDao = database.quotaSnapshotDao(),
                refreshAttemptDao = database.refreshAttemptDao(),
                sessionStore = sessionStore,
                deviceCodeLoginDiagnosticsReader = deviceCodeLoginDiagnosticsReader,
                notificationPreferenceReader = notificationPreferences,
                retentionPreferenceReader = retentionPreferences,
            )

            return AppContainer(
                refreshCoordinator = refreshCoordinator,
                accountListUseCase = accountListRepository,
                accountDeleteUseCase = AccountDeleteUseCase { providerId, localAccountId ->
                    accountDeletionRepository.deleteAccount(
                        providerId = providerId,
                        localAccountId = localAccountId,
                    )
                },
                accountSwitchUseCase = accountMutationRepository,
                accountRenameUseCase = accountMutationRepository,
                accountQuotaAlertEvaluationRequester = AccountQuotaAlertEvaluationRequester { providerId, localAccountId, windowId ->
                    currentQuotaStatePublisher.publish(
                        currentQuotaStateRepository.loadAccountState(
                            providerId = providerId,
                            localAccountId = localAccountId,
                            primaryWindowId = windowId,
                        ),
                    )
                },
                homeCurrentQuotaStateLoader = currentQuotaStateRepository,
                homeRefreshUseCase = homeRefreshUseCase,
                deviceCodeLoginController = deviceCodeLoginController,
                deviceCodeLoginControllers = mapOf(
                    ProviderId("codex") to deviceCodeLoginController,
                ),
                deviceCodeLoginNotifier = deviceCodeLoginNotifier,
                retentionPreferences = retentionPreferences,
                notificationPreferences = RepublishingNotificationPreferenceStore(
                    delegate = notificationPreferences,
                    currentQuotaStateLoader = currentQuotaStateRepository,
                    currentQuotaStatePublisher = currentQuotaStatePublisher,
                ),
                primaryQuotaWindowPreferences = primaryQuotaWindowPreferences,
                quotaHistoryClearUseCase = RepublishingQuotaHistoryClearUseCase(
                    delegate = quotaHistoryClearRepository,
                    currentQuotaStateLoader = currentQuotaStateRepository,
                    currentQuotaStatePublisher = currentQuotaStatePublisher,
                ),
                homeTrendHistoryLoader = homeTrendHistoryRepository,
                backgroundRefreshStatusReader = backgroundRefreshStatusRepository,
                settingsDiagnosticsReader = settingsDiagnosticsReader,
                widgetQuotaStateLoader = widgetQuotaStateRepository,
                startupMaintenance = StartupMaintenance(
                    retentionPreferenceReader = retentionPreferences,
                    retentionCleanup = retentionCleanupRepository,
                    reporter = AndroidStartupMaintenanceReporter,
                    clock = clock,
                ),
                claudeConnect = claudeAccountConnector,
                appearancePreferences = appearancePreferences,
                initialThemeMode = initialThemeMode,
                currencyPreferences = currencyPreferences,
                exchangeRateRepository = exchangeRateRepository,
                currentAccountStore = currentQuotaRefreshAccountStore,
                exchangeRateRefresher = exchangeRateRefresher,
                notificationWindowChoicesLoader = DefaultNotificationWindowChoicesLoader(
                    currentAccountReader = currentAccountReader,
                    quotaSnapshotDao = database.quotaSnapshotDao(),
                ),
            )
        }

        private const val PREFERENCES_FILE_NAME = "codexmeter.preferences_pb"
        private const val APPEARANCE_PREFERENCES_FILE_NAME = "appearance.preferences_pb"
        private const val SESSION_DIRECTORY_NAME = "sessions"
    }
}

private class CodexSessionEnvelopeFactory(
    private val sessionCipher: CodexSessionCipher,
) : CodexSessionImporter.SessionEnvelopeFactory {
    override fun create(
        payload: CodexSessionPayload,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        now: Instant,
    ): ProviderSessionEnvelope {
        val envelope = ProviderSessionEnvelope(
            providerId = CODEX_PROVIDER_ID.value,
            localAccountId = localAccountId.value,
            providerAccountId = providerAccountId?.value,
            schemaVersion = CODEX_SESSION_SCHEMA_VERSION,
            payloadCiphertext = byteArrayOf(),
            payloadNonce = byteArrayOf(),
            createdAt = now.toString(),
            updatedAt = now.toString(),
        )
        return sessionCipher.encrypt(
            session = payload,
            envelope = envelope,
            updatedAt = now,
        )
    }

    private companion object {
        const val CODEX_SESSION_SCHEMA_VERSION = 1
    }
}

private val CODEX_PROVIDER_ID = ProviderId("codex")
