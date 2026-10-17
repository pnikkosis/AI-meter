package com.kmnexus.codexmeter.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.domain.theme.ThemeMode
import com.kmnexus.codexmeter.ui.components.CodexMeterBackdrop
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import java.time.Instant

/**
 * Debug-only preview of Home with two accounts (one used up, one with two windows), so the cards can
 * be checked in light and dark. It follows the system uiMode (ThemeMode.SYSTEM). Launch:
 *   adb shell am start -n <debug package>/com.kmnexus.codexmeter.ui.home.HomePreviewActivity
 * then `adb exec-out screencap -p`.
 *
 * Fake data only: no real account, no network, no credentials.
 */
class HomePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodexMeterTheme(themeMode = ThemeMode.SYSTEM) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CodexMeterBackdrop(modifier = Modifier.fillMaxSize())
                    HomeScreen(uiState = previewHomeUiState())
                }
            }
        }
    }
}

private fun previewHomeUiState(): HomeUiState {
    val now = Instant.now()
    return HomeUiState(
        isLoading = false,
        isRefreshing = false,
        accounts = listOf(
            HomeAccountCardUi(
                localAccountId = "preview-codex",
                providerId = "codex",
                providerAccountId = null,
                providerName = "Codex",
                displayName = "Personal",
                planName = "Pro 20x",
                windows = listOf(
                    HomeWindowUi(
                        windowId = "weekly",
                        labelResId = R.string.account_quota_weekly_label,
                        labelName = null,
                        percentLeft = 0,
                        valueText = null,
                        level = QuotaLevel.Depleted,
                        resetAt = now.plusSeconds(4 * 24 * 3600L),
                    ),
                ),
                status = HomeAccountStatus.Fresh,
                lastUpdatedAt = now,
                errorMessageResId = null,
            ),
            HomeAccountCardUi(
                localAccountId = "preview-claude",
                providerId = "claude",
                providerAccountId = null,
                providerName = "Claude",
                displayName = "Trabajo",
                planName = null,
                windows = listOf(
                    HomeWindowUi(
                        windowId = "claude_5h_window",
                        labelResId = R.string.account_quota_five_hour_label,
                        labelName = null,
                        percentLeft = 67,
                        valueText = null,
                        level = QuotaLevel.Normal,
                        resetAt = now.plusSeconds(3 * 3600L),
                    ),
                    HomeWindowUi(
                        windowId = "claude_7d_window",
                        labelResId = R.string.window_label_7d,
                        labelName = null,
                        percentLeft = 8,
                        valueText = null,
                        level = QuotaLevel.Warning,
                        resetAt = now.plusSeconds(6 * 24 * 3600L),
                    ),
                ),
                status = HomeAccountStatus.Stale,
                lastUpdatedAt = now.minusSeconds(50 * 60L),
                errorMessageResId = null,
            ),
        ),
        hiddenAccountCount = 1,
        lastUpdatedAt = now,
    )
}
