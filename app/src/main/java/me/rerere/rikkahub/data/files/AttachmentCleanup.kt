package me.rerere.rikkahub.data.files

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Avatar
import java.io.File

/**
 * 附件清理：删掉没人引用的文件。
 *
 * 标记-清除而不是引用计数：引用点分散在消息 JSON、助手头像、用户头像里，计数要在每一处删除
 * 路径上维护，漏一处就少算、少算就误删；这里只要求扫描覆盖面，漏一处最坏是少收一点垃圾。
 *
 * 只手动触发，只在 `upload/` 目录里动。判据是「消息里有没有提过这个相对路径」，走
 * [me.rerere.rikkahub.data.db.dao.MessageNodeDAO.hasFileReference]：它按子串匹配，所以
 * 跨包名的绝对路径也能认出来，方向偏保守。
 */
class AttachmentCleanup(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {

    data class Report(val deleted: Int, val freedBytes: Long, val kept: Int)

    suspend fun collect(): Report = withContext(Dispatchers.IO) {
        val candidates = FilesCleanupPlanner.candidatesIn(context.filesDir, FileFolders.UPLOAD)
        if (candidates.isEmpty()) return@withContext Report(0, 0, 0)

        val settings = settingsStore.awaitLoaded()
        val avatarPaths = buildSet {
            settings.assistants.forEach { assistant ->
                (assistant.avatar as? Avatar.Image)?.url?.let(::add)
                assistant.background?.let(::add)
            }
            (settings.displaySetting.userAvatar as? Avatar.Image)?.url?.let(::add)
        }.mapNotNull { url -> url.substringAfter("/files/", "").takeIf { it.isNotEmpty() } }.toSet()

        var deleted = 0
        var freed = 0L
        var kept = 0
        for (candidate in candidates) {
            val referenced = candidate in avatarPaths || database.messageNodeDao().hasFileReference(candidate)
            if (referenced) {
                kept++
                continue
            }
            val file = File(context.filesDir, candidate)
            val size = file.length()
            if (file.delete()) {
                deleted++
                freed += size
            }
        }
        Report(deleted = deleted, freedBytes = freed, kept = kept)
    }
}
