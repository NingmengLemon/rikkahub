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
 * 用于备份里的库版本高于本仓库的情况：迁移链只能往上升，降不了，所以要用读取层按官方快照
 * 读出来、再写进一个新建的当前版本库。转换完的库交给现有的 `stageRestore` 流程继续走，
 * 附件、settings、publish 都不变。
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
                OfficialDatabaseWriter(room.openHelper.writableDatabase, filesUriPrefix).write(reader)
                // WAL 里可能还压着内容，先合进主文件再关，否则留下非空的 -wal 会让
                // PendingRestore 对同一路径生成两条 entry
                DatabaseBackup.checkpoint(room.openHelper.writableDatabase)
            } finally {
                room.close()
            }
        }
        DatabaseBackup.removeSidecars(target)
    }

    companion object {
        /**
         * 读库里的 `user_version` 并取对应的快照；读不出来或版本未知时返回 null。
         *
         * 返回 null 只表示「不是我们能识别的官方库」，调用方应当回到迁移链，而不是直接失败。
         */
        fun snapshotOf(file: File): OfficialSnapshot? = runCatching {
            val database = android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            )
            try {
                OfficialSnapshots.forVersion(database.version)
            } finally {
                database.close()
            }
        }.getOrNull()
    }
}
