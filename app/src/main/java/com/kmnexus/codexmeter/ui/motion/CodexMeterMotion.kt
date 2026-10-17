package com.kmnexus.codexmeter.ui.motion

object CodexMeterMotion {
    const val PercentDurationMillis = 620
    const val ProgressRailDurationMillis = 620
    const val PageCascadeDurationMillis = 420
    const val PageCascadeInitialOffsetDp = 14f
    const val AccountDrawerDurationMillis = 200

    fun shouldAnimatePercent(oldPercent: Int?, newPercent: Int?, animatorsEnabled: Boolean): Boolean =
        animatorsEnabled && newPercent != null && oldPercent != newPercent

    fun progressFraction(percent: Int?): Float =
        ((percent ?: 0).toFloat() / 100f).coerceIn(0f, 1f)

    fun initialPercentTarget(percent: Int?, animatorsEnabled: Boolean): Int =
        if (animatorsEnabled && percent != null) 0 else percent ?: 0

    fun initialProgressTarget(percent: Int?, animatorsEnabled: Boolean): Float =
        if (animatorsEnabled && percent != null) 0f else progressFraction(percent)

    fun pageCascadeInitialOffsetDp(animatorsEnabled: Boolean): Float =
        if (animatorsEnabled) PageCascadeInitialOffsetDp else 0f
}
