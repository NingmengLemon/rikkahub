package me.rerere.rikkahub.data.files

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Avatar
import java.io.File

/**
 * 附件回收的扫描：哪些文件没人引用了。
 *
 * 目前只扫不删。原因是引用点没有全部收进库：草稿和消息队列只存在内存里，扫描看不见它们，
 * 而内容寻址命中复用文件时不会刷新 mtime，所以「跳过最近写入的文件」这类时间启发式挡不住
 * 「老文件 + 新的未发送引用」。把可回收名单报给用户是安全的，真删不是。
 *
 * 只扫 `upload/`。技能、字体、媒体创作各有自己的生命周期，不在范围内。
 *
 * 引用来源：消息 JSON、助手头像与背景、用户头像、收藏快照。
 */
class AttachmentCleanup(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
) {

    /** 扫一遍的结果。`reclaimableBytes` 是这些孤儿占的空间，现在不动它们。 */
    data class Report(val orphaned: Int, val reclaimableBytes: Long, val kept: Int)

    suspend fun scan(): Report = withContext(Dispatchers.IO) {
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

        // 收藏存的是消息快照，图片引用也在里面；消息删掉、收藏还在时不能算孤儿。
        // 一次读完所有收藏的 JSON，避免每个候选查一遍库。
        val favoriteJson = database.favoriteDao().listAll().first().joinToString("\n") {
            it.refJson + "\n" + it.snapshotJson + "\n" + (it.metaJson ?: "")
        }

        var orphaned = 0
        var reclaimable = 0L
        var kept = 0
        for (candidate in candidates) {
            if (isReferenced(candidate, avatarPaths, favoriteJson)) {
                kept++
                continue
            }
            orphaned++
            reclaimable += File(context.filesDir, candidate).length()
        }
        Report(orphaned = orphaned, reclaimableBytes = reclaimable, kept = kept)
    }

    /** 删一个文件的结果。还被人引用时不删，只让调用方把它从列表里摘掉。 */
    enum class Outcome { DELETED, STILL_REFERENCED, MISSING }

    /**
     * 没人引用了才删这一个文件。
     *
     * 逐个文件删是安全的：查引用是同步做完的，不依赖「最近写入」这类时间启发式。批量按时间
     * 删不安全，所以没有对应的批量删除。
     */
    suspend fun deleteIfUnreferenced(relativePath: String): Outcome = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, relativePath)
        if (!file.isFile) return@withContext Outcome.MISSING
        val settings = settingsStore.awaitLoaded()
        val avatarPaths = buildSet {
            settings.assistants.forEach { assistant ->
                (assistant.avatar as? Avatar.Image)?.url?.let(::add)
                assistant.background?.let(::add)
            }
            (settings.displaySetting.userAvatar as? Avatar.Image)?.url?.let(::add)
        }.mapNotNull { url -> url.substringAfter("/files/", "").takeIf { it.isNotEmpty() } }.toSet()
        val favoriteJson = database.favoriteDao().listAll().first().joinToString("\n") {
            it.refJson + "\n" + it.snapshotJson + "\n" + (it.metaJson ?: "")
        }
        if (isReferenced(relativePath, avatarPaths, favoriteJson)) {
            return@withContext Outcome.STILL_REFERENCED
        }
        if (file.delete()) Outcome.DELETED else Outcome.MISSING
    }

    /**
     * 候选在库里是否还被提到。
     *
     * 消息里存的是 `Uri.fromFile(...)` 的结果，会做百分号编码；磁盘上的文件名不做编码，
     * 所以除了裸路径还要按 URI 编码后的形状再找一次。文件名里的反斜杠在 JSON 里也是转义的。
     *
     * 判据是子串匹配，方向偏保守：命中就保留。漏判只会让名单少一项，而名单目前不执行。
     */
    private suspend fun isReferenced(
        candidate: String,
        avatarPaths: Set<String>,
        favoriteJson: String,
    ): Boolean {
        if (candidate in avatarPaths) return true
        if (candidate in favoriteJson) return true
        val encoded = Uri.encode(candidate, "/")
        if (encoded != candidate && encoded in favoriteJson) return true
        val dao = database.messageNodeDao()
        if (dao.hasFileReference(candidate)) return true
        if (encoded != candidate && dao.hasFileReference(encoded)) return true
        return candidate.contains('\\') && dao.hasFileReference(candidate.replace("\\", "\\\\"))
    }
}
