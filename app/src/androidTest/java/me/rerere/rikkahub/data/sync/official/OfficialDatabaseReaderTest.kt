package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 读取层的正向与负向验证。
 *
 * 正向用一个结构等同官方 v32 的库（由应用自己创建，它的 schema 与官方快照一致），负向用临时
 * 改坏的库。真正的官方备份样本要等拿到之后补进来。
 */
@RunWith(AndroidJUnit4::class)
class OfficialDatabaseReaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workspace get() = File(context.cacheDir, "official-reader-test")

    @After fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test fun readsEveryTableOfTheAppDatabase() {
        val file = appDatabase("positive.db") {
            it.execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, type) " +
                    "VALUES ('w1', 'demo', '/tmp/demo', 'READY', 7, 8, 'LOCAL')"
            )
        }

        OfficialDatabaseReader.open(file).use { reader ->
            assertTrue("应用库不该有快照之外的列", reader.unexpectedColumns.isEmpty())
            for (table in OfficialDatabaseSchema.TABLES.keys) {
                assertTrue(reader.rows(table).count() >= 0)
            }
            assertEquals(1, reader.rows("workspaces").count())
        }
    }

    @Test fun rowValuesMatchTheSnapshotAffinity() {
        val file = appDatabase("values.db") {
            it.execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, type) " +
                    "VALUES ('w1', 'demo', '/tmp/demo', 'READY', 7, 8, 'LOCAL')"
            )
        }

        OfficialDatabaseReader.open(file).use { reader ->
            val row = reader.rows("workspaces").single()
            assertEquals("w1", row.string("id"))
            assertEquals("/tmp/demo", row.string("root"))
            assertEquals("LOCAL", row.string("type"))
            assertEquals(7L, row.long("created_at"))
        }
    }

    @Test
    fun failsWhenTheSnapshotTableIsMissing() {
        val file = rawDatabase("missing-table.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE unrelated (id TEXT)")
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        assertTrue("缺表必须失败，实际是 $error", error is IllegalStateException)
        assertTrue("错误信息要指出缺表，实际是 ${error?.message}", error?.message.orEmpty().contains("没有表"))
    }

    @Test
    fun failsWhenAColumnIsMissing() {
        val file = rawDatabase("missing-column.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            // 只建一张缺列的 ConversationEntity，遍历顺序里它排在第一位，所以命中的是缺列分支
            database.execSQL("CREATE TABLE ConversationEntity (id TEXT PRIMARY KEY)")
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        assertTrue("缺列必须失败，实际是 $error", error is IllegalStateException)
        assertTrue("错误信息要指出缺列，实际是 ${error?.message}", error?.message.orEmpty().contains("缺少列"))
    }

    @Test
    fun failsWhenTheSchemaVersionDiffers() {
        val file = appDatabase("version.db") {}
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.version = OfficialDatabaseSchema.VERSION + 1
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        assertTrue("版本不符必须失败，实际是 $error", error is IllegalStateException)
        assertTrue("错误信息要指出版本，实际是 ${error?.message}", error?.message.orEmpty().contains("版本"))
    }

    @Test
    fun reportsColumnsTheSnapshotDoesNotKnow() {
        val file = appDatabase("extra-column.db") {
            it.execSQL("ALTER TABLE workspaces ADD COLUMN future_column TEXT")
        }
        OfficialDatabaseReader.open(file).use { reader ->
            assertEquals(listOf("future_column"), reader.unexpectedColumns["workspaces"])
            // 多出来的列不影响读取
            assertTrue(reader.rows("workspaces").count() >= 0)
        }
    }

    /** 用应用自己的 schema 建一个库，可选地往里写点数据 */
    private fun appDatabase(name: String, prepare: (SupportSQLiteDatabase) -> Unit = {}): File {
        val file = rawDatabase(name)
        val room = AppDatabaseFactory.create(context, file.absolutePath)
        try {
            prepare(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }
        return file
    }

    private fun rawDatabase(name: String): File {
        workspace.mkdirs()
        val file = File(workspace, name)
        file.delete()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
        return file
    }
}
