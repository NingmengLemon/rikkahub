package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import me.rerere.tts.provider.TTSProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class DefaultProvidersTest {
    @Test
    fun `default providers should include vercel ai gateway with expected balance config`() {
        val vercelProviders = DEFAULT_PROVIDERS
            .filterIsInstance<ProviderSetting.OpenAI>()
            .filter { it.name == "Vercel AI Gateway" }

        assertEquals(1, vercelProviders.size)

        val provider = vercelProviders.single()
        assertEquals("https://ai-gateway.vercel.sh/v1", provider.baseUrl)
        assertFalse(provider.enabled)
        assertTrue(provider.builtIn)
        assertTrue(provider.balanceOption.enabled)
        assertEquals("/credits", provider.balanceOption.apiPath)
        assertEquals("balance", provider.balanceOption.resultPath)
    }

    @Test
    fun `sponsor providers are no longer preset`() {
        val preset = DEFAULT_PROVIDERS.map { it.id }.toSet()

        assertTrue(preset.intersect(REMOVED_SPONSOR_PROVIDER_IDS).isEmpty())
    }

    @Test
    fun `sponsor providers left in stored settings are dropped`() {
        val sponsorId = REMOVED_SPONSOR_PROVIDER_IDS.first()
        val keptId = Uuid.random()
        val settings = Settings(
            providers = listOf(
                ProviderSetting.OpenAI(id = sponsorId, name = "sponsor", baseUrl = "https://a.example/v1", apiKey = ""),
                ProviderSetting.OpenAI(id = keptId, name = "kept", baseUrl = "https://b.example/v1", apiKey = ""),
            ),
        )

        val normalized = settings.normalized()

        assertEquals(listOf(keptId), normalized.providers.map { it.id })
    }

    @Test
    fun `deleted preset provider is not restored`() {
        val remaining = DEFAULT_PROVIDERS.drop(1)

        val normalized = Settings(providers = remaining).normalized()

        assertEquals(remaining.map { it.id }, normalized.providers.map { it.id })
    }

    @Test
    fun `sponsor tts provider left in stored settings is dropped`() {
        val sponsorId = REMOVED_SPONSOR_TTS_PROVIDER_IDS.first()
        val settings = Settings(
            ttsProviders = listOf(
                TTSProviderSetting.SystemTTS(id = DEFAULT_SYSTEM_TTS_ID, name = ""),
                TTSProviderSetting.OpenAI(
                    id = sponsorId,
                    name = "sponsor",
                    baseUrl = "https://a.example/v1",
                    model = "tts",
                    voice = "alloy",
                ),
            ),
        )

        val normalized = settings.normalized()

        assertEquals(listOf(DEFAULT_SYSTEM_TTS_ID), normalized.ttsProviders.map { it.id })
    }
}
