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
 * 官方恢复用的是 [JsonInstant]，它开着 `ignoreUnknownKeys`，所以我们往 `Settings` 里加
 * 字段后，官方读到的多余字段会被直接忽略，不会导致恢复失败。当初 `launchCount` 就是这么
 * 混过去的：它不在 `Settings` 里，官方也照读不误。
 *
 * 这条测试把该行为钉住。一旦有人关掉 `ignoreUnknownKeys`，这里会失败，那时官方兼容导出
 * 就必须改成真正裁剪字段。
 *
 * 边界：`ignoreUnknownKeys` 只覆盖「不认识的 key」。改已有字段的含义、或者让它变成官方
 * 不认识的类型，它救不了，所以 README 里那条「不改现有序列化字段含义」的约束依然有效。
 */
class OfficialSettingsCompatibilityTest {

    @Test
    fun `decoder tolerates fields it does not know`() {
        val settings = Settings()
        val baseline = JsonInstant.encodeToJsonElement(Settings.serializer(), settings)

        val withExtra = JsonObject(baseline.jsonObject + ("fieldOnlyWeKnow" to JsonPrimitive("value")))
        val decoded = JsonInstant.decodeFromString(Settings.serializer(), withExtra.toString())

        assertEquals(
            baseline,
            JsonInstant.encodeToJsonElement(Settings.serializer(), decoded),
        )
    }
}
