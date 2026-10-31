package me.rerere.rikkahub.data.sync.official

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 把读到的官方行写进一个新建的库。
 *
 * 目标是当前版本的空库，不是正在用的库：没有清空这一步，也没有与运行中会话抢写的问题。
 * 整批写入包在一个事务里，失败即整个临时库作废。
 *
 * 行级照抄：JSON 字段（`UIMessage`、`config` 之类）原样搬运，不解析再序列化。解析会丢未知
 * 字段，也会把官方的格式变化变成我们这边的转换错误。表顺序由 [OfficialTableOrder] 按外键
 * 推出，父表先插。
 */
internal class OfficialDatabaseWriter(
    private val database: SupportSQLiteDatabase,
    /** 当前应用的 files 目录 URI 前缀，用来重定向官方备份里的绝对路径 */
    private val filesUriPrefix: String,
) {

    /** 返回写入的总行数 */
    fun write(reader: OfficialDatabaseReader): Int {
        var written = 0
        database.beginTransaction()
        try {
            for (tableName in OfficialTableOrder.insertionOrder(reader.tables)) {
                val table = reader.tables.getValue(tableName)
                reader.forEachRow(tableName) { row ->
                    insert(table, row)
                    written++
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return written
    }

    private fun insert(table: OfficialSchemaTypes.Table, row: OfficialRow) {
        val values = ContentValues(table.columns.size)
        for (column in table.columns) {
            when (val value = row.value(column.name)) {
                null -> Unit
                is Long -> values.put(column.name, value)
                is Double -> values.put(column.name, value)
                // 文本列可能嵌着附件引用，统一重定向到当前安装
                is String -> values.put(column.name, OfficialUrlRewriter.rewriteEmbedded(value, filesUriPrefix))
                is ByteArray -> values.put(column.name, value)
                else -> error("未知的列值类型：${table.name}.${column.name} = ${value::class}")
            }
        }
        val rowId = database.insert(table.name, SQLiteDatabase.CONFLICT_ABORT, values)
        // requery 的实现在失败时抛异常，这里是兜底，防止哪次换实现后静默丢行
        check(rowId != -1L) { "插入 ${table.name} 失败" }
    }
}
