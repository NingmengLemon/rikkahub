package me.rerere.rikkahub.data.sync.official

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
 * 写入层的往返验证：把一个库读出来，写进新建的空库，再从新库读回来比对。
 *
 * 用应用自己的库当来源，这样不需要官方样本也能把写入路径跑通；真正的官方备份验收还要等
 * 样本到位。
 */
@RunWith(AndroidJUnit4::class)
class OfficialDatabaseWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workspace get() = File(context.cacheDir, "official-writer-test")

    @After fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test fun roundTripsRowsIntoAFreshDatabase() {
        val source = appDatabase("source.db") { database ->
            database.execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, type) " +
                    "VALUES ('w1', 'demo', '/tmp/demo', 'READY', 7, 8, 'LOCAL')"
            )
        }

        val target = rawDatabase("target.db")
        val room = AppDatabaseFactory.create(context, target.absolutePath)
        val written: Int
        try {
            OfficialDatabaseReader.open(source).use { reader ->
                written = OfficialDatabaseWriter(room.openHelper.writableDatabase).write(reader)
            }
        } finally {
            room.close()
        }
        assertEquals(1, written)

        OfficialDatabaseReader.open(target).use { reader ->
            for (table in OfficialDatabaseSchema.TABLES.keys) {
                val expected = if (table == "workspaces") 1 else 0
                assertEquals("表 $table 的行数", expected, reader.rows(table).count())
            }
            val row = reader.rows("workspaces").single()
            assertEquals("w1", row.string("id"))
            assertEquals("/tmp/demo", row.string("root"))
            assertEquals("LOCAL", row.string("type"))
            assertEquals(7L, row.long("created_at"))
        }
    }

    @Test fun writesNothingWhenTheSourceIsEmpty() {
        val source = appDatabase("empty-source.db") {}
        val target = rawDatabase("empty-target.db")
        val room = AppDatabaseFactory.create(context, target.absolutePath)
        val written: Int
        try {
            OfficialDatabaseReader.open(source).use { reader ->
                written = OfficialDatabaseWriter(room.openHelper.writableDatabase).write(reader)
            }
        } finally {
            room.close()
        }
        assertEquals(0, written)
        OfficialDatabaseReader.open(target).use { reader ->
            assertEquals(0, reader.rows("workspaces").count())
        }
    }

    /**
     * 带外键的两张表一起搬，顺便验证 JSON 字段原样不动。
     *
     * 只写 workspaces（无外键）的实现在前一条测试里也能通过，这条才真正踩到「父表先插」。
     */
    @Test fun roundTripsForeignKeyRowsAndRawJson() {
        val messages = """[{"id":"a1","role":"USER","parts":[{"type":"text","text":"你好"}]}]"""
        val source = appDatabase("fk-source.db") { database ->
            database.execSQL(
                "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) " +
                    "VALUES ('c1', 'demo', '[]', 1, 2)"
            )
            database.execSQL(
                "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                    "VALUES ('m1', 'c1', 0, ?, 3)",
                arrayOf(messages),
            )
        }

        val target = rawDatabase("fk-target.db")
        val room = AppDatabaseFactory.create(context, target.absolutePath)
        try {
            OfficialDatabaseReader.open(source).use { reader ->
                assertEquals(2, OfficialDatabaseWriter(room.openHelper.writableDatabase).write(reader))
            }
        } finally {
            room.close()
        }

        OfficialDatabaseReader.open(target).use { reader ->
            val node = reader.rows("message_node").single()
            assertEquals("m1", node.string("id"))
            assertEquals("c1", node.string("conversation_id"))
            assertEquals(0L, node.long("node_index"))
            assertEquals(3L, node.long("select_index"))
            assertEquals("JSON 字段必须逐字节原样搬运", messages, node.string("messages"))
            assertEquals("c1", reader.rows("ConversationEntity").single().string("id"))
        }
    }

    /** 用应用自己的 schema 建一个库，可选地往里写点数据 */
    private fun appDatabase(name: String, prepare: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit = {}): File {
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
