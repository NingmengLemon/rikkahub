package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
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
 * 正向用的是应用自己创建的库：它的结构与官方 v32 快照一致，所以能证明「照快照读」这条路
 * 走得通。负向用临时建出来的残缺库，证明缺表、缺列会被拦住而不是静默读出半截数据。
 */
@RunWith(AndroidJUnit4::class)
class OfficialDatabaseReaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<File>()

    @After fun tearDown() {
        created.forEach {
            File(it.path + "-wal").delete()
            File(it.path + "-shm").delete()
            it.delete()
        }
        created.clear()
    }

    @Test fun readsEveryTableOfTheAppDatabase() {
        val name = "reader-positive"
        // build() 是惰性的，要先碰一次库才会真正落盘
        val room = AppDatabaseFactory.create(context, name)
        room.openHelper.writableDatabase
        room.close()
        val file = context.getDatabasePath(name).also { created.add(it) }

        OfficialDatabaseReader.open(file).use { reader ->
            assertTrue("应用库不该有快照之外的列", reader.unexpectedColumns.isEmpty())
            for (table in OfficialDatabaseSchema.TABLES.keys) {
                val count = reader.rows(table).count()
                assertTrue("表 $table 读出来的行数不该是负数", count >= 0)
            }
        }
    }

    @Test fun rowValuesMatchTheSnapshotAffinity() {
        val name = "reader-values"
        val room = AppDatabaseFactory.create(context, name)
        room.openHelper.writableDatabase.execSQL(
            "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, type) " +
                "VALUES ('w1', 'demo', '/tmp/demo', 'READY', 7, 8, 'LOCAL')"
        )
        room.close()
        val file = context.getDatabasePath(name).also { created.add(it) }

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
        val file = temporaryDatabase("reader-missing-table")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE unrelated (id TEXT)")
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        assertTrue("缺表必须失败，实际是 $error", error is IllegalStateException)
    }

    @Test
    fun failsWhenAColumnIsMissing() {
        val file = temporaryDatabase("reader-missing-column")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            // 只建一个缺列的表，其余表同样缺失，第一个检查就会失败
            database.execSQL("CREATE TABLE conversationentity (id TEXT PRIMARY KEY)")
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        assertTrue("缺列必须失败，实际是 $error", error is IllegalStateException)
    }

    private fun temporaryDatabase(name: String): File {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        return file.also { created.add(it) }
    }
}
