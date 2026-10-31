package me.rerere.rikkahub.data.files

import java.io.File

/**
 * 附件清理的判定：哪些文件没人引用了。
 *
 * 用标记-清除而不是引用计数：引用计数要在消息删除、分支切换、助手删除、导入覆盖每一处维护，
 * 漏一处就少算，少算的后果是误删用户文件；这里只要求「扫描覆盖所有引用点」，漏一处最坏是
 * 少收一点垃圾，方向是安全的。
 *
 * 纯函数，不依赖 Android：调用方传入「在用的相对路径集合」与「磁盘上的候选文件」。
 */
internal object FilesCleanupPlanner {

    /**
     * @param referenced 仍然被引用的相对路径（形如 `upload/<hash>.png`）
     * @param candidates 磁盘上现有文件的相对路径
     * @return 可以删掉的那些，按路径排序
     */
    fun orphaned(referenced: Set<String>, candidates: Collection<String>): List<String> =
        candidates.filter { it !in referenced }.sorted()

    /**
     * 从一段 JSON 里收集引用的相对路径。
     *
     * 消息里的附件引用是绝对 `file://` URI，包名与用户号都在路径里，所以这里只取
     * `files/` 之后的部分作为相对路径；认不出来的形状直接跳过，不猜。
     *
     * 只认 [FileFolders.UPLOAD]：聊天附件都在那里，别的目录（技能、字体、媒体创作）各有自己的
     * 生命周期，不在这里动。
     */
    fun referencedPathsIn(json: String): List<String> {
        if (!json.contains("file://")) return emptyList()
        return FILE_URI.findAll(json).mapNotNull { match ->
            val tail = match.groupValues[1]
            val folder = tail.substringBefore('/')
            if (folder == FileFolders.UPLOAD && '/' in tail) tail else null
        }.toList()
    }

    /** 从目录里列出候选相对路径；只收文件，符号链接与目录跳过 */
    fun candidatesIn(root: File, folder: String): List<String> {
        val directory = File(root, folder)
        if (!directory.isDirectory) return emptyList()
        return directory.walkTopDown()
            .filter { it.isFile }
            .map { "$folder/" + it.relativeTo(directory).invariantSeparatorsPath }
            .toList()
    }

    private val FILE_URI = Regex("""file://[^"\s]*?/files/([^"\s]+)""")
}
