package com.kmnexus.codexmeter.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kmnexus.codexmeter.domain.model.AccountStatus
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.model.SnapshotId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaFreshness
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Renders the real widget pipeline (WidgetQuotaStateFactory + the Glance layout) for each state the
 * widget must tell apart, in English and Spanish, and writes PNGs to `files/widget-shots/` of the
 * app under test. Run on an emulator or device; the emulator workflow pulls them with `run-as`.
 */
@RunWith(AndroidJUnit4::class)
class WidgetScreenshotTest {

    private data class Spec(
        val name: String,
        val variant: WidgetLayoutVariant,
        val widthDp: Int,
        val heightDp: Int,
        val state: WidgetQuotaState,
    )

    private val factory = WidgetQuotaStateFactory()
    private val now: Instant = Instant.now()

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun renderWidgetStates() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val outDir = File(appContext.filesDir, "widget-shots").apply { mkdirs() }
        val glance = GlanceRemoteViews()
        for (locale in listOf(Locale.US, Locale.forLanguageTag("es-AR"))) {
            val configuration = Configuration(appContext.resources.configuration).apply { setLocale(locale) }
            val context = appContext.createConfigurationContext(configuration)
            for (spec in specs()) {
                val size = DpSize(spec.widthDp.dp, spec.heightDp.dp)
                val remoteViews = glance.compose(context, size) {
                    CodexMeterWidgetContent(context, spec.state, size, spec.variant)
                }.remoteViews
                val density = context.resources.displayMetrics.density
                val widthPx = (spec.widthDp * density).toInt()
                val heightPx = (spec.heightDp * density).toInt()
                val view = remoteViews.apply(context, FrameLayout(context))
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, widthPx, heightPx)
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                // Wallpaper-like backdrop so any transparent area is visible.
                Canvas(bitmap).apply { drawColor(Color.rgb(60, 64, 72)) }.also { view.draw(it) }
                FileOutputStream(File(outDir, "widget_${locale.language}_${spec.name}.png")).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    private fun specs(): List<Spec> {
        val codexWindows = listOf(
            window("five_hour", used = 13, resetIn = Duration.ofMinutes(133), seconds = 5 * 3600),
            window("weekly", used = 58, resetIn = Duration.ofDays(3), seconds = 7 * 86400),
        )
        val claudeWindows = listOf(
            window("claude_5h_window", used = 36, resetIn = Duration.ofMinutes(95), seconds = 5 * 3600),
            window("claude_7d_window", used = 81, resetIn = Duration.ofDays(4), seconds = 7 * 86400),
            window("claude_7d_opus_window", used = 100, resetIn = Duration.ofDays(4), seconds = 7 * 86400)
                .copy(availability = QuotaWindowAvailability.Depleted),
            window("claude_7d_sonnet_window", used = 20, resetIn = Duration.ofDays(4), seconds = 7 * 86400),
        )
        val codexFresh = state("codex", "Codex personal", codexWindows, fetchedAgo = Duration.ofMinutes(3))
        val claudeFresh = state("claude", "Claude Max", claudeWindows, fetchedAgo = Duration.ofMinutes(2))
        fun failed(base: CurrentQuotaState, error: QuotaError) =
            base.copy(status = CurrentQuotaStatus.ErrorWithLastKnownGood, error = error)
        fun withoutData(base: CurrentQuotaState, status: CurrentQuotaStatus, error: QuotaError?) =
            base.copy(status = status, snapshot = null, primaryWindow = null, secondaryWindows = emptyList(), error = error)

        return listOf(
            Spec("codex_fresh_4x1", WidgetLayoutVariant.FourByOne, 340, 110, factory.create(codexFresh)),
            Spec("codex_fresh_3x1", WidgetLayoutVariant.ThreeByOne, 252, 110, factory.create(codexFresh)),
            Spec("claude_fresh_4x2", WidgetLayoutVariant.FourByTwo, 340, 210, factory.create(claudeFresh)),
            Spec("claude_fresh_3x2", WidgetLayoutVariant.ThreeByTwo, 252, 210, factory.create(claudeFresh)),
            Spec(
                "codex_offline_4x1", WidgetLayoutVariant.FourByOne, 340, 110,
                factory.create(failed(codexFresh, QuotaError.Network(diagnosticsDigest = "d"))),
            ),
            Spec(
                "claude_rate_limited_3x2", WidgetLayoutVariant.ThreeByTwo, 252, 210,
                factory.create(failed(claudeFresh, QuotaError.RateLimited(diagnosticsDigest = "d"))),
            ),
            Spec(
                "codex_provider_error_4x1", WidgetLayoutVariant.FourByOne, 340, 110,
                factory.create(failed(codexFresh, QuotaError.ProviderUnavailable(httpStatus = 503, diagnosticsDigest = "d"))),
            ),
            Spec(
                "claude_unreadable_4x1", WidgetLayoutVariant.FourByOne, 340, 110,
                factory.create(failed(claudeFresh, QuotaError.ResponseInvalid(diagnosticsDigest = "d"))),
            ),
            Spec(
                "codex_stale_4x1", WidgetLayoutVariant.FourByOne, 340, 110,
                factory.create(codexFresh.copy(status = CurrentQuotaStatus.PossiblyStale)),
            ),
            Spec(
                "codex_expired_yesterday_4x1", WidgetLayoutVariant.FourByOne, 340, 110,
                factory.create(
                    state("codex", "Codex personal", codexWindows, fetchedAgo = Duration.ofHours(26))
                        .copy(status = CurrentQuotaStatus.Expired),
                ),
            ),
            Spec(
                "claude_auth_required_old_data_4x1", WidgetLayoutVariant.FourByOne, 340, 110,
                factory.create(
                    claudeFresh.copy(
                        status = CurrentQuotaStatus.AuthRequired,
                        error = QuotaError.AuthRequired(httpStatus = 401, diagnosticsDigest = "d"),
                    ),
                ),
            ),
            Spec(
                "claude_auth_required_no_data_3x2", WidgetLayoutVariant.ThreeByTwo, 252, 210,
                factory.create(
                    withoutData(claudeFresh, CurrentQuotaStatus.AuthRequired, QuotaError.AuthRequired(401, "d")),
                ),
            ),
            Spec(
                "codex_offline_no_data_3x2", WidgetLayoutVariant.ThreeByTwo, 252, 210,
                factory.create(withoutData(codexFresh, CurrentQuotaStatus.NoData, QuotaError.Network("d"))),
            ),
            Spec(
                "codex_no_data_yet_3x1", WidgetLayoutVariant.ThreeByOne, 252, 110,
                factory.create(withoutData(codexFresh, CurrentQuotaStatus.NoData, null)),
            ),
            Spec("unconfigured_3x2", WidgetLayoutVariant.ThreeByTwo, 252, 210, factory.unconfigured(hasAccounts = true)),
            Spec("no_accounts_3x1", WidgetLayoutVariant.ThreeByOne, 252, 110, factory.unconfigured(hasAccounts = false)),
        )
    }

    private fun window(id: String, used: Int, resetIn: Duration, seconds: Int) = QuotaWindow(
        windowId = QuotaWindowId(id),
        titleKey = id,
        usedPercent = used,
        resetAt = now.plus(resetIn),
        limitWindowSeconds = seconds,
        isPrimaryCandidate = true,
        availability = QuotaWindowAvailability.Available,
    )

    private fun state(
        provider: String,
        accountName: String,
        windows: List<QuotaWindow>,
        fetchedAgo: Duration,
    ): CurrentQuotaState {
        val fetchedAt = now.minus(fetchedAgo)
        val account = ProviderAccount(
            localAccountId = LocalAccountId("acc-$provider"),
            providerId = ProviderId(provider),
            providerAccountId = ProviderAccountId("p-$provider"),
            displayName = accountName,
            avatarInitial = accountName.take(1),
            avatarColorKey = "acc-$provider",
            status = AccountStatus.Active,
            createdAt = fetchedAt,
            updatedAt = fetchedAt,
            lastSuccessfulRefreshAt = fetchedAt,
        )
        val snapshot = QuotaSnapshot(
            snapshotId = SnapshotId("s-$provider"),
            providerId = ProviderId(provider),
            localAccountId = account.localAccountId,
            providerAccountId = account.providerAccountId,
            fetchedAt = fetchedAt,
            source = QuotaSnapshotSource.ManualRefresh,
            planType = null,
            windows = windows,
            credits = null,
            responseDigest = null,
        )
        return CurrentQuotaState(
            status = CurrentQuotaStatus.Fresh,
            freshness = CurrentQuotaFreshness.Fresh,
            account = account,
            snapshot = snapshot,
            latestAttempt = null,
            primaryWindow = windows.firstOrNull(),
            secondaryWindows = windows.drop(1),
            primaryWindowCanAlert = false,
            error = null,
        )
    }
}
