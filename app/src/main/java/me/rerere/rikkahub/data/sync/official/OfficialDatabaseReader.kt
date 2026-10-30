package me.rerere.rikkahub.data.sync.official

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

/**
 * 官方库的只读访问层。
 *
 * 表名与列名全部来自 [OfficialDatabaseSchema] 的冻结快照，不引用本仓库的 `@Entity`：官方库的
 * 结构怎么变、我们自己的库怎么演进，都不影响这里的读法。
 *
 * 打开时会核对每张表的列是否与快照一致。快照里有、库里没有的列说明官方做了破坏性迁移，
 * 直接失败；库里多出来的列忽略并记日志。读业务表，`message_fts*` 这类派生表跳过。
 */
internal class OfficialDatabaseReader private constructor(
    private val database: SQLiteDatabase,
) : Closeable {

    /** 库里有、快照里没有的列，诊断用 */
    val unexpectedColumns: Map<String, List<String>> = verifySchema()

    fun rows(tableName: String): Sequence<OfficialRow> {
        val table = requireNotNull(OfficialDatabaseSchema.TABLES[tableName]) {
            "未知的官方表：$tableName"
        }
        val projection = table.columns.joinToString(",") { "`${it.name}`" }
        val cursor = database.rawQuery("SELECT $projection FROM `$tableName`", null)
        return cursorToRows(cursor, table)
    }

    override fun close() {
        database.close()
    }

    private fun verifySchema(): Map<String, List<String>> {
        val unexpected = LinkedHashMap<String, List<String>>()
        for ((name, table) in OfficialDatabaseSchema.TABLES) {
            val liveColumns = database.rawQuery("PRAGMA table_info(`$name`)", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            check(liveColumns.isNotEmpty()) { "官方库里没有表 $name" }
            val expected = table.columns.map { it.name }
            val missing = expected.filter { it !in liveColumns }
            check(missing.isEmpty()) { "表 $name 缺少列 ${missing.joinToString()}" }
            val extra = liveColumns.filter { it !in expected }
            if (extra.isNotEmpty()) unexpected[name] = extra
        }
        return unexpected
    }

    /**
     * 每行在迭代时就把值读出来，不把 cursor 交给调用方。
     *
     * [OfficialRow] 如果在 Sequence 消费完之后还被持有，带着 cursor 的版本会直接越界；官方
     * 数据要跨阶段使用（校验、转换），所以行对象必须自包含。
     */
    private fun cursorToRows(cursor: Cursor, table: OfficialDatabaseSchema.Table): Sequence<OfficialRow> = sequence {
        try {
            while (cursor.moveToNext()) {
                val values = LinkedHashMap<String, Any?>(table.columns.size)
                for (column in table.columns) {
                    val index = cursor.getColumnIndex(column.name)
                    values[column.name] = if (index < 0 || cursor.isNull(index)) {
                        null
                    } else {
                        readValue(cursor, index, column)
                    }
                }
                yield(OfficialRow(values))
            }
        } finally {
            cursor.close()
        }
    }

    companion object {
        fun open(databaseFile: File): OfficialDatabaseReader {
            check(databaseFile.isFile) { "官方数据库不存在：${databaseFile.absolutePath}" }
            return OfficialDatabaseReader(
                SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            )
        }

        private fun readValue(
            cursor: Cursor,
            index: Int,
            column: OfficialDatabaseSchema.Column,
        ): Any = when (column.affinity.uppercase()) {
            "INTEGER" -> cursor.getLong(index)
            "REAL" -> cursor.getDouble(index)
            "TEXT" -> cursor.getString(index)
            "BLOB" -> cursor.getBlob(index)
            else -> error("未处理的 affinity：${column.affinity}")
        }
    }
}

/**
 * 一行官方数据。
 *
 * 按 [OfficialDatabaseSchema.Column] 的 affinity 在读取时就转好类型，调用方拿到的是确定的
 * 类型；行对象不依赖 cursor，Sequence 结束之后仍然可读。
 */
internal class OfficialRow(
    private val values: Map<String, Any?>,
) {
    fun value(columnName: String): Any? = values[columnName]

    fun string(columnName: String): String? = value(columnName) as String?

    fun long(columnName: String): Long? = value(columnName) as Long?

    fun isNull(columnName: String): Boolean = value(columnName) == null
}
