package com.kmnexus.codexmeter.providers.claude

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.providers.claude.dto.ClaudeUsageResponseDto
import com.kmnexus.codexmeter.providers.claude.mapper.ClaudeUsageMapper
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeLimitsMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `limits rows map to windows in server order with used and remaining percent`() {
        val windows = windowsOf(
            """
            {"limits":[
              {"kind":"session","group":"session","percent":37,"resets_at":"2026-10-17T15:00:00Z","severity":"normal","is_active":true},
              {"kind":"weekly_all","group":"weekly","percent":64,"resets_at":"2026-10-21T00:00:00Z","severity":"warning","is_active":false},
              {"kind":"weekly_scoped","group":"weekly","percent":81,"resets_at":"2026-10-21T00:00:00Z","scope":{"model":{"display_name":"Opus"}},"severity":"warning","is_active":false}
            ]}
            """,
        )

        assertEquals(
            listOf("claude_5h_window", "claude_7d_window", "claude_7d_opus_window"),
            windows.map { it.windowId.value },
        )
        val session = windows[0]
        assertEquals(37, session.usedPercent)
        assertEquals(63, session.remainingPercent)
        assertEquals(Instant.parse("2026-10-17T15:00:00Z"), session.resetAt)
        assertEquals(5 * 3600, session.limitWindowSeconds)
        assertTrue(session.isPrimaryCandidate)
        assertEquals(7 * 86400, windows[1].limitWindowSeconds)
        assertEquals("Opus", windows[2].subLabel)
    }

    @Test
    fun `an unknown scoped model keeps the server label under a stable id`() {
        val windows = windowsOf(
            """{"limits":[{"kind":"weekly_scoped","group":"weekly","percent":5,"resets_at":null,"scope":{"model":{"display_name":"Claude Fable 5"}}}]}""",
        )

        assertEquals("claude_7d_scoped_claude_fable_5", windows.single().windowId.value)
        assertEquals("Claude Fable 5", windows.single().subLabel)
        assertNull(windows.single().resetAt)
    }

    @Test
    fun `over-limit and missing percentages are never shown as available quota`() {
        val windows = windowsOf(
            """
            {"limits":[
              {"kind":"session","percent":112,"resets_at":"2026-10-17T15:00:00Z"},
              {"kind":"weekly_all","percent":null,"resets_at":"2026-10-21T00:00:00Z"}
            ]}
            """,
        )

        assertEquals(100, windows[0].usedPercent)
        assertEquals(0, windows[0].remainingPercent)
        assertEquals(QuotaWindowAvailability.Depleted, windows[0].availability)
        assertNull(windows[1].usedPercent)
        assertNull(windows[1].remainingPercent)
        assertEquals(QuotaWindowAvailability.Missing, windows[1].availability)
    }

    @Test
    fun `spend row uses the extra usage amounts as its label`() {
        val windows = windowsOf(
            """
            {"extra_usage":{"is_enabled":true,"monthly_limit":50000,"used_credits":27140,"utilization":54,"currency":"USD"},
             "limits":[{"kind":"spend","group":"monthly","percent":54,"resets_at":"2026-11-01T00:00:00Z","is_active":true}]}
            """,
        )

        assertEquals("claude_extra_usage", windows.single().windowId.value)
        assertEquals(54, windows.single().usedPercent)
        assertEquals("$271.40 / $500.00", windows.single().subLabel)
    }

    @Test
    fun `unknown meter kinds are still shown under a neutral id`() {
        val windows = windowsOf("""{"limits":[{"kind":"daily_routine","group":"daily","percent":3}]}""")

        assertEquals("claude_daily_routine", windows.single().windowId.value)
        assertEquals("daily", windows.single().subLabel)
        assertNull(windows.single().limitWindowSeconds)
    }

    @Test
    fun `legacy fields are used when the server sends no rows`() {
        val legacy = """{"five_hour":{"utilization":20,"resets_at":"2026-10-17T15:00:00Z"},"seven_day":{"utilization":30}}"""
        val emptyRows = """{"limits":[],"five_hour":{"utilization":20},"seven_day":{"utilization":30}}"""

        listOf(legacy, emptyRows).forEach { body ->
            val windows = windowsOf(body)
            assertEquals(listOf("claude_5h_window", "claude_7d_window"), windows.map { it.windowId.value })
            assertEquals(20, windows[0].usedPercent)
        }
    }

    @Test
    fun `a response without any meter maps to no windows`() {
        assertTrue(windowsOf("""{"limits":[]}""").isEmpty())
        assertTrue(windowsOf("""{}""").isEmpty())
        assertTrue(windowsOf("""{"limits":[{"group":"weekly","percent":10}]}""").isEmpty())
    }

    @Test
    fun `snapshot carries the account identity`() {
        val dto = json.decodeFromString<ClaudeUsageResponseDto>("""{"limits":[{"kind":"session","percent":1}]}""")
        val snapshot = ClaudeUsageMapper.map(
            dto = dto,
            localAccountId = LocalAccountId("claude-1"),
            providerAccountId = ProviderAccountId("account-uuid-1"),
            fetchedAt = Instant.parse("2026-10-17T12:00:00Z"),
            source = QuotaSnapshotSource.ManualRefresh,
        )

        assertEquals("claude", snapshot.providerId.value)
        assertEquals("account-uuid-1", snapshot.providerAccountId?.value)
        assertEquals(QuotaSnapshotSource.ManualRefresh, snapshot.source)
    }

    private fun windowsOf(body: String) =
        ClaudeUsageMapper.windows(json.decodeFromString<ClaudeUsageResponseDto>(body.trimIndent()))
}
