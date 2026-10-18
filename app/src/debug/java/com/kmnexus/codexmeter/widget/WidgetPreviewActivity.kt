package com.kmnexus.codexmeter.widget

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.lifecycleScope
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Debug-only preview that lays real widget content over the live wallpaper, for visual review.
 * Launch per spec (account-3x1, account-4x1, account-3x2, account-4x2, account-unconfigured,
 * accounts-3x2, accounts-4x2, accounts-4x3, accounts-empty):
 *   adb shell am start -n <package>/com.kmnexus.codexmeter.widget.WidgetPreviewActivity --es spec accounts-4x2
 * then `adb exec-out screencap -p`.
 */
class WidgetPreviewActivity : ComponentActivity() {

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        setContentView(root)

        val spec = specFor(intent?.getStringExtra("spec") ?: "accounts-4x2")

        lifecycleScope.launch {
            val size = DpSize(spec.widthDp.dp, spec.heightDp.dp)
            val remoteViews = GlanceRemoteViews().compose(this@WidgetPreviewActivity, size) {
                spec.content(size)
            }.remoteViews
            val container = FrameLayout(this@WidgetPreviewActivity)
            container.addView(remoteViews.apply(this@WidgetPreviewActivity, container))
            root.addView(
                container,
                LinearLayout.LayoutParams((spec.widthDp * density).toInt(), (spec.heightDp * density).toInt()),
            )
        }
    }

    private class Spec(
        val widthDp: Int,
        val heightDp: Int,
        val content: @androidx.compose.runtime.Composable (DpSize) -> Unit,
    )

    private fun specFor(name: String): Spec {
        val now = Instant.now()
        fun field(id: String, percent: Int, level: QuotaLevel, seconds: Int?) = WidgetField(
            windowId = id, isBalance = false, percent = percent,
            balanceAmount = null, balanceCurrency = null,
            resetAt = now.plusSeconds(3 * 3600), level = level, limitWindowSeconds = seconds,
        )
        val account = WidgetQuotaState(
            status = WidgetQuotaStatus.Fresh, providerName = "Claude", providerId = "claude",
            localAccountId = "a1", accountName = "Trabajo", level = QuotaLevel.Caution,
            clickTarget = WidgetClickTarget.Home,
            fields = listOf(
                field("claude_5h_window", 67, QuotaLevel.Normal, FIVE_HOURS),
                field("claude_7d_window", 22, QuotaLevel.Caution, WEEK),
                field("claude_7d_opus_window", 8, QuotaLevel.Warning, WEEK),
                field("claude_7d_sonnet_window", 0, QuotaLevel.Depleted, WEEK),
            ),
            isUnconfigured = false, hasAccounts = true, lastUpdatedAt = now,
        )
        fun window(id: String, left: Int, level: QuotaLevel, seconds: Int?) = AccountsWidgetWindow(
            windowId = id, limitWindowSeconds = seconds, percentLeft = left, level = level,
            resetAt = now.plusSeconds(3 * 3600).toString(),
        )
        val rows = listOf(
            AccountsWidgetRow(
                providerId = "codex", localAccountId = "c1", providerName = "Codex", accountName = "Personal",
                status = WidgetQuotaStatus.Fresh,
                windows = listOf(window("weekly", 0, QuotaLevel.Depleted, WEEK)),
                headlineWindowId = "weekly", lastUpdatedAt = now.toString(),
            ),
            AccountsWidgetRow(
                providerId = "claude", localAccountId = "a1", providerName = "Claude", accountName = "Trabajo",
                status = WidgetQuotaStatus.Fresh,
                windows = listOf(
                    window("claude_5h_window", 67, QuotaLevel.Normal, FIVE_HOURS),
                    window("claude_7d_window", 22, QuotaLevel.Caution, WEEK),
                ),
                headlineWindowId = "claude_7d_window", lastUpdatedAt = now.toString(),
            ),
            AccountsWidgetRow(
                providerId = "codex", localAccountId = "c2", providerName = "Codex", accountName = "Equipo",
                status = WidgetQuotaStatus.ErrorWithLastKnownGood,
                windows = listOf(window("five_hour", 54, QuotaLevel.Unknown, FIVE_HOURS)),
                headlineWindowId = "five_hour", lastUpdatedAt = now.minusSeconds(2 * 3600).toString(),
                errorMessageKey = "error_network",
            ),
            AccountsWidgetRow(
                providerId = "claude", localAccountId = "a2", providerName = "Claude", accountName = "Personal",
                status = WidgetQuotaStatus.AuthRequired, windows = emptyList(), headlineWindowId = null,
            ),
        )
        val accounts = AccountsWidgetState(rows = rows, hasAccounts = true)
        fun accountSpec(width: Int, height: Int, variant: WidgetLayoutVariant, state: WidgetQuotaState = account) =
            Spec(width, height) { size -> CodexMeterWidgetContent(this, state, size, variant) }
        fun accountsSpec(width: Int, height: Int, state: AccountsWidgetState = accounts) =
            Spec(width, height) { size -> AccountsWidgetContent(this, state, size) }
        return when (name) {
            "account-3x1" -> accountSpec(252, 110, WidgetLayoutVariant.ThreeByOne)
            "account-4x1" -> accountSpec(340, 110, WidgetLayoutVariant.FourByOne)
            "account-3x2" -> accountSpec(252, 210, WidgetLayoutVariant.ThreeByTwo)
            "account-4x2" -> accountSpec(340, 210, WidgetLayoutVariant.FourByTwo)
            "account-unconfigured" -> accountSpec(
                252, 110, WidgetLayoutVariant.ThreeByOne,
                WidgetQuotaStateFactory().unconfigured(hasAccounts = true),
            )
            "accounts-3x2" -> accountsSpec(252, 210)
            "accounts-4x3" -> accountsSpec(340, 300)
            "accounts-empty" -> accountsSpec(252, 210, AccountsWidgetState())
            else -> accountsSpec(340, 210)
        }
    }

    private companion object {
        const val FIVE_HOURS = 5 * 60 * 60
        const val WEEK = 7 * 24 * 60 * 60
    }
}
