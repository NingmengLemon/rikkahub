package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import java.io.File

/**
 * 我们的数据库已经不在官方形状上，而官方兼容导出需要的裁剪逻辑还没实现。
 *
 * 用异常而不是返回空值，是为了让调用方只有一个失败分支；具体原因留给日志，界面上换成
 * 一条可翻译的提示。
 */
internal class OfficialExportUnsupportedException(
    detail: String,
) : IllegalStateException(detail)

/**
 * 把我们的数据库快照转成官方客户端能打开的库。
 *
 * 当前双方 schema 一致，原样返回即可；一旦发现分叉就抛 [OfficialExportUnsupportedException]。
 * 静默产出一份官方打不开的备份比直接失败更糟：失败当场就能看见，坏备份要等到恢复时才发现。
 */
internal object OfficialDatabaseAdapter {

    /**
     * 返回可以写进归档的数据库文件。
     *
     * 现在只会返回 [snapshot] 本身。将来的裁剪逻辑在这里按 [OfficialDatabaseSchema] 重建表、
     * 把 `user_version` 与 `room_master_table` 的 identity hash 改回官方值。
     */
    fun adaptForOfficial(snapshot: File): File {
        val (version, identityHash) = readHeader(snapshot)
        return when (val verdict = OfficialDatabaseCompatibility.compare(version, identityHash)) {
            OfficialDatabaseCompatibility.Verdict.Identical -> snapshot
            is OfficialDatabaseCompatibility.Verdict.Diverged -> throw OfficialExportUnsupportedException(
                "官方兼容导出还不能处理分叉的 schema：${verdict.reason}。裁剪逻辑尚未实现。"
            )
        }
    }

    private fun readHeader(snapshot: File): Pair<Int, String?> {
        val database = SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            // room_master_table 是 Room 自己维护的单行表。表不存在（更早的库）按「身份未知」处理，
            // 由 compare 判成 Diverged。数据库损坏或磁盘 IO 错误不属于这一类，原样抛出去，
            // 否则会被误报成「schema 分叉」而掩盖真正的问题。
            val identityHash = runCatching {
                database.rawQuery("SELECT identity_hash FROM room_master_table LIMIT 1", null)
                    .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrElse { error ->
                val tolerable = error is SQLiteException &&
                    error !is SQLiteDatabaseCorruptException &&
                    error !is SQLiteDiskIOException
                if (tolerable) null else throw error
            }
            version to identityHash
        } finally {
            database.close()
        }
    }
}
