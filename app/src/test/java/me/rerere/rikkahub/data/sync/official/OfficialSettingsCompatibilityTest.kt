package me.rerere.rikkahub.data.sync.official

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方兼容导出不需要裁 `settings.json`，这个前提靠解码器行为保证。
 *
 * 官方恢复用的就是 [JsonInstant]，它开着 `ignoreUnknownKeys`，所以我们以后往
 * `Settings` 里加字段时，官方读到的多余字段会被直接忽略，不会导致恢复失败。
 * 这条测试把该行为钉住：一旦有人把 `ignoreUnknownKeys` 关掉，这里会失败，
 * 那时官方兼容导出就必须改成真正裁剪字段。
 */
class OfficialSettingsCompatibilityTest {

    @Test
    fun `decoder tolerates fields it does not know`() {
        val settings = Settings()
        val baseline = JsonInstant.encodeToJsonElement(Settings.serializer(), settings)

        val withExtra = JsonObject(baseline.jsonObject + ("fieldOnlyWeKnow" to JsonPrimitive("value")))
        val decoded = JsonInstant.decodeFromString(Settings.serializer(), withExtra.toString())

        assertEquals(
            JsonInstant.encodeToJsonElement(Settings.serializer(), settings),
            JsonInstant.encodeToJsonElement(Settings.serializer(), decoded),
        )
    }
}
