package me.rerere.rikkahub.data.sync.official

/**
 * 官方 RikkaHub 备份归档的物理布局。
 *
 * 导入侧按它识别条目，官方兼容导出按它重建目录。官方不认识我们新增的任何目录，
 * 所以导出时必须把附件放回这里列出的位置。
 *
 * 与 [OfficialDatabaseSchema] 一样，这份清单是冻结的，只随官方版本更新；
 * `OfficialBackupLayoutTest` 会拿它和仓库里在用的常量对账。
 */
internal object OfficialBackupLayout {
    /** 官方 settings.json 的条目名 */
    const val SETTINGS_ENTRY = "settings.json"

    /** 官方数据库快照的条目名 */
    const val DATABASE_ENTRY = "rikka_hub.db"

    /** 更早的官方备份可能带上的 WAL 侧文件，恢复侧保留它们只为兼容旧备份 */
    val legacyDatabaseEntries: Set<String> = setOf("rikka_hub-wal", "rikka_hub-shm")

    /** settings.json 里不属于 Settings 本身的字段，早期备份就带着 */
    const val LAUNCH_COUNT_KEY = "launchCount"

    /** 官方归档会带上的附件目录 */
    val attachmentFolders: Set<String> = setOf("upload", "skills", "fonts", "media_creation")

    /** 这些目录保留子目录，其余目录只有平铺文件 */
    val nestedAttachmentFolders: Set<String> = setOf("skills", "media_creation")

    /** 归档条目是否落在某个附件目录下 */
    fun isAttachmentEntry(name: String): Boolean = name.substringBefore('/') in attachmentFolders
}
