package com.kmnexus.codexmeter.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexMeterMotionTest {
    @Test
    fun `percent animation runs for first numeric value and changed values when animators are enabled`() {
        assertTrue(CodexMeterMotion.shouldAnimatePercent(oldPercent = 40, newPercent = 62, animatorsEnabled = true))
        assertTrue(CodexMeterMotion.shouldAnimatePercent(oldPercent = null, newPercent = 62, animatorsEnabled = true))
        assertFalse(CodexMeterMotion.shouldAnimatePercent(oldPercent = 62, newPercent = 62, animatorsEnabled = true))
        assertFalse(CodexMeterMotion.shouldAnimatePercent(oldPercent = 40, newPercent = 62, animatorsEnabled = false))
        assertFalse(CodexMeterMotion.shouldAnimatePercent(oldPercent = 62, newPercent = null, animatorsEnabled = true))
    }

    @Test
    fun `progress fraction clamps percent into rail bounds`() {
        assertEquals(0f, CodexMeterMotion.progressFraction(null), 0.0001f)
        assertEquals(0f, CodexMeterMotion.progressFraction(-10), 0.0001f)
        assertEquals(0.62f, CodexMeterMotion.progressFraction(62), 0.0001f)
        assertEquals(1f, CodexMeterMotion.progressFraction(130), 0.0001f)
    }

    @Test
    fun `first paint targets start at the visible motion origin when animators are enabled`() {
        assertEquals(0, CodexMeterMotion.initialPercentTarget(percent = 62, animatorsEnabled = true))
        assertEquals(62, CodexMeterMotion.initialPercentTarget(percent = 62, animatorsEnabled = false))
        assertEquals(0f, CodexMeterMotion.initialProgressTarget(percent = 62, animatorsEnabled = true), 0.0001f)
        assertEquals(0.62f, CodexMeterMotion.initialProgressTarget(percent = 62, animatorsEnabled = false), 0.0001f)
    }

    @Test
    fun `quota number and rail durations are synchronized and slower`() {
        assertEquals(CodexMeterMotion.PercentDurationMillis, CodexMeterMotion.ProgressRailDurationMillis)
        assertTrue(CodexMeterMotion.PercentDurationMillis >= 560)
    }

    @Test
    fun `page cascade respects disabled system animations`() {
        assertEquals(0f, CodexMeterMotion.pageCascadeInitialOffsetDp(animatorsEnabled = false), 0.0001f)
        assertTrue(CodexMeterMotion.pageCascadeInitialOffsetDp(animatorsEnabled = true) > 0f)
    }
}
