package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.datastore.NetworkSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 自动重试暂时整体下线，[Settings.retryPolicy] 恒返回 NONE；`NetworkSetting` 的字段与
 * 已保存的设置保留，恢复接线时只需要改回实现。
 */
class SettingsRetryPolicyTest {
    private fun settings(retries: Int, enabled: Boolean = true) = Settings.dummy().copy(
        networkSetting = NetworkSetting(
            enableAutoRetry = enabled,
            maxRetries = retries,
        )
    )

    @Test
    fun `retries are always disabled`() {
        assertEquals(0, settings(5).retryPolicy().maxRetries)
        assertEquals(0, settings(5, enabled = false).retryPolicy().maxRetries)
        assertEquals(0, settings(0).retryPolicy().maxRetries)
        assertEquals(0, settings(99).retryPolicy().maxRetries)
    }

    @Test
    fun `old settings JSON without the field uses the default`() {
        val decoded = JsonInstant.decodeFromString<NetworkSetting>(
            """{"enableAutoRetry":true}"""
        )

        assertEquals(3, decoded.maxRetries)
    }
}
