package me.rerere.rikkahub.data.sync.official

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 把官方库转换成当前版本库这一步的验证。
 *
 * 用设备上那份官方 v32 样本当输入（真实数据），转出来的库要能被读取层重新打开、行数与来源一致。
 */
@RunWith(AndroidJUnit4::class)
class OfficialDatabaseConverterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workspace get() = File(context.cacheDir, "official-converter-test")

    @After fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test fun convertsTheOfficialSampleIntoACurrentDatabase() {
        val sample = File("/data/local/tmp/rikkahub-sample-v32.db")
        assumeTrue("没有放官方样本，跳过", sample.isFile)

        workspace.mkdirs()
        val target = File(workspace, "converted.db")
        runBlocking { OfficialDatabaseConverter(context).convert(sample, target) }

        assertTrue("转换结果要落盘", target.isFile)
        OfficialDatabaseReader.open(target).use { reader ->
            assertTrue(reader.unexpectedColumns.isEmpty())
            assertEquals(168, reader.rows("ConversationEntity").count())
            assertEquals(673, reader.rows("message_node").count())
            assertEquals(97, reader.rows("managed_files").count())
            assertEquals(476, reader.rows("usage_record").count())
        }
    }

    @Test fun readsTheHeaderOfTheProvidedSamples() {
        // 恢复流程靠这两处元数据分流：版本决定能不能交给迁移链，指纹决定要不要转换
        val v32 = File("/data/local/tmp/rikkahub-sample-v32.db")
        assumeTrue("没有放 v32 样本，跳过", v32.isFile)
        OfficialDatabaseHeaders.read(v32)?.let { header ->
            assertEquals(32, header.version)
            assertEquals(OfficialDatabaseSchema.IDENTITY_HASH, header.identityHash)
        } ?: error("应当能读出 v32 样本的 header")
    }
}
