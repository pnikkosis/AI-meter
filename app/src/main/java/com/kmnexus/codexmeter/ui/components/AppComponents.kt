package com.kmnexus.codexmeter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kmnexus.codexmeter.ui.theme.CodexMeterShapes
import com.kmnexus.codexmeter.ui.theme.CodexMeterSpacing
import com.kmnexus.codexmeter.ui.theme.CodexMeterTheme
import java.util.Locale

/** Plain app background. Drawn behind every destination so nothing shows through page motion. */
@Composable
fun CodexMeterBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(CodexMeterTheme.colors.neutral))
}

/** The app's one card style: a flat surface with a hairline border. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(CodexMeterSpacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(CodexMeterShapes.lg)
            .background(CodexMeterTheme.colors.surface)
            .border(1.dp, CodexMeterTheme.colors.border, CodexMeterShapes.lg)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * The provider's name as an outlined uppercase chip ("CODEX", "CLAUDE"). Text instead of a logo:
 * the app uses no other company's marks.
 */
@Composable
fun ProviderChip(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name.uppercase(Locale.ROOT),
        modifier = modifier
            .border(1.dp, CodexMeterTheme.colors.borderStrong, CodexMeterShapes.xs)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        ),
        color = CodexMeterTheme.colors.secondary,
        maxLines = 1,
    )
}

/** A rounded usage bar; [fraction] is the share still available (0..1). */
@Composable
fun UsageBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = CodexMeterTheme.colors.track,
) {
    val filled = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CodexMeterShapes.pill)
            .background(trackColor),
    ) {
        if (filled > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled)
                    .clip(CodexMeterShapes.pill)
                    .background(color),
            )
        }
    }
}

/**
 * Pull-to-refresh indicator: a flat disc that follows the pull and spins while refreshing.
 * [PullToRefreshDefaults.IndicatorBox] handles the drop-down position and the enter/exit motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefreshIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
) {
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
    ) {
        val fraction = state.distanceFraction.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    val scale = 0.6f + 0.4f * fraction
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.3f + 0.7f * fraction
                }
                .background(CodexMeterTheme.colors.surface, CircleShape)
                .border(1.dp, CodexMeterTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CodexMeterTheme.colors.accent,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
