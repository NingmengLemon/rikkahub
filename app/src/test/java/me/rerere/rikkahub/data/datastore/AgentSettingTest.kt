package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
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
    fun `timeouts round trip`() {
        val decoded = JsonInstant.decodeFromString<AgentSetting>(
            """{"toolTimeoutSeconds":0,"shellDefaultTimeoutSeconds":45,"shellMaxTimeoutSeconds":1200}"""
        )

        assertEquals(0, decoded.toolTimeoutSeconds)
        assertEquals(45, decoded.shellDefaultTimeoutSeconds)
        assertEquals(1200, decoded.shellMaxTimeoutSeconds)
    }
}
