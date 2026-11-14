package me.rerere.rikkahub.data.ai

import me.rerere.ai.agent.AgentRequest
import me.rerere.rikkahub.data.datastore.AgentSetting
import me.rerere.rikkahub.data.datastore.NetworkSetting
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SettingsTimeoutsTest {
    @Test
    fun `stream watchdog follows the switch`() {
        val enabled = Settings.dummy().copy(
            networkSetting = NetworkSetting(streamIdleTimeoutEnabled = true),
        )
        val disabled = Settings.dummy().copy(
            networkSetting = NetworkSetting(streamIdleTimeoutEnabled = false),
        )

        assertEquals(AgentRequest.DEFAULT_STREAM_IDLE_TIMEOUT, enabled.streamIdleWatchdog())
        assertNull(disabled.streamIdleWatchdog())
    }

    @Test
    fun `tool timeout is disabled by zero`() {
        assertEquals(300.seconds, Settings.dummy().toolExecutionTimeout())
        assertNull(
            Settings.dummy().copy(
                agentSetting = AgentSetting(toolTimeoutSeconds = 0),
            ).toolExecutionTimeout(),
        )
    }
}
