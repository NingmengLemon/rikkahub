package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSettingTest {
    @Test
    fun `old settings without the section use the defaults`() {
        val decoded = JsonInstant.decodeFromString<Settings>("""{"init":true}""")

        assertEquals(300, decoded.agentSetting.toolTimeoutSeconds)
        assertEquals(30, decoded.agentSetting.shellDefaultTimeoutSeconds)
        assertEquals(600, decoded.agentSetting.shellMaxTimeoutSeconds)
    }

    @Test
    fun `timeouts survive a serialization round trip`() {
        val original = AgentSetting(
            toolTimeoutSeconds = 0,
            shellDefaultTimeoutSeconds = 45,
            shellMaxTimeoutSeconds = 1200,
        )

        val decoded = JsonInstant.decodeFromString<AgentSetting>(
            JsonInstant.encodeToString(original)
        )

        assertEquals(original, decoded)
    }

    @Test
    fun `values are clamped to their valid ranges`() {
        val normalized = AgentSetting(
            toolTimeoutSeconds = -5,
            shellDefaultTimeoutSeconds = 9_000,
            shellMaxTimeoutSeconds = 1,
        ).normalized()

        assertEquals(0, normalized.toolTimeoutSeconds)
        assertEquals(AgentSetting.MIN_SHELL_TIMEOUT_SECONDS, normalized.shellMaxTimeoutSeconds)
        assertEquals(AgentSetting.MIN_SHELL_TIMEOUT_SECONDS, normalized.shellDefaultTimeoutSeconds)
    }

    @Test
    fun `tool timeout snaps to whole minutes and shell times to the page bounds`() {
        val normalized = AgentSetting(
            toolTimeoutSeconds = 45,
            shellDefaultTimeoutSeconds = 2,
            shellMaxTimeoutSeconds = 30,
        ).normalized()

        assertEquals(AgentSetting.TOOL_TIMEOUT_STEP_SECONDS, normalized.toolTimeoutSeconds)
        assertEquals(AgentSetting.MIN_SHELL_TIMEOUT_SECONDS, normalized.shellMaxTimeoutSeconds)
        assertEquals(AgentSetting.MIN_SHELL_DEFAULT_TIMEOUT_SECONDS, normalized.shellDefaultTimeoutSeconds)
    }

    @Test
    fun `stream idle watchdog is enabled for old network settings`() {
        val decoded = JsonInstant.decodeFromString<NetworkSetting>("""{}""")

        assertTrue(decoded.streamIdleTimeoutEnabled)
    }
}
