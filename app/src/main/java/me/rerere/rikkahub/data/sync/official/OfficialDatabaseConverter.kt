package me.rerere.rikkahub.data.sync.official

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.sync.DatabaseBackup
import java.io.File

/**
 * 把官方库转换成当前版本的空库。
 *
 * 用于「版本相同但结构不同」的库：迁移链不会管，Room 的 identity 校验会直接拒绝，所以要用
 * 读取层按官方快照读出来、再写进一个新建的当前版本库。转换完交给现有的 `stageRestore` 流程
 * 继续走，附件、settings、publish 都不变。
 *
 * 只转换，不安装：目标文件写在 staging 下，由调用方决定怎么用。
 */
internal class OfficialDatabaseConverter(
    private val context: Context,
) {

    /**
     * @param source 备份里解压出来的官方库
     * @param target 转换结果，必须是不存在的路径（[AppDatabaseFactory] 会创建它）
     */
    suspend fun convert(source: File, target: File) = withContext(Dispatchers.IO) {
        val filesUriPrefix = context.filesDir.toUri().toString().trimEnd('/') + "/"
        OfficialDatabaseReader.open(source).use { reader ->
            val room = AppDatabaseFactory.create(context, target.absolutePath)
            try {
                val database = room.openHelper.writableDatabase
                requireTargetCoversSnapshot(database, reader.tables)
                OfficialDatabaseWriter(database, filesUriPrefix).write(reader)
                // WAL 里可能还压着内容，先合进主文件再关，否则留下非空的 -wal 会让
                // PendingRestore 对同一路径生成两条 entry
                DatabaseBackup.checkpoint(database)
            } finally {
                room.close()
            }
        }
        DatabaseBackup.removeSidecars(target)
    }

    /**
     * 快照里有、目标库没有的列直接拒绝，不要等到插入时才报错。
     *
     * 官方升版通常就是加列（v31 到 v32 加了 `workspaces.type` 与 `workspaces.ssh_config`），
     * 所以这个检查在「源比我们新」时必然触发；那种情况本来就该明确失败，而不是写一半再崩。
     */
    private fun requireTargetCoversSnapshot(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        tables: Map<String, OfficialSchemaTypes.Table>,
    ) {
        for ((name, table) in tables) {
            val live = database.query("PRAGMA table_info(`$name`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            if (live.isEmpty()) continue
            val missing = table.columns.map { it.name }.filter { it !in live }
            check(missing.isEmpty()) {
                "官方表 $name 有本应用还不认识的列 ${missing.joinToString()}，无法直接转换"
            }
        }
    }
}
