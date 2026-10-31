package me.rerere.rikkahub.data.sync.official

/**
 * 官方数据库结构的注册表：版本号到快照的映射。
 *
 * 每个官方版本一份快照，数据从 `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/<version>.json`
 * 生成。读取层先看库里的 `user_version`，再取对应的这份快照，所以官方升版时只要重新生成、
 * 在这里注册一行。
 *
 * 这份清单是「已知官方版本」：不在清单里的版本明确失败，不猜。
 *
 * 官方库里还有 `message_fts` 这类由手写 SQL 建的全文索引表，它们不在 Room 的 schema 里，也
 * 不在快照里。那是派生数据：`AppDatabaseFactory` 打开数据库时只保证表存在，索引内容要靠
 * [me.rerere.rikkahub.data.repository.ConversationRepository.rebuildAllIndexes] 重建，
 * 所以导入完成后要跑一次重建，否则搜索会是空的。
 */
internal object OfficialSnapshots {

    val BY_VERSION: Map<Int, OfficialSnapshot> = listOf(
        OfficialSnapshot(OfficialSnapshotV31.VERSION, OfficialSnapshotV31.IDENTITY_HASH, OfficialSnapshotV31.TABLES),
        OfficialSnapshot(OfficialSnapshotV32.VERSION, OfficialSnapshotV32.IDENTITY_HASH, OfficialSnapshotV32.TABLES),
    ).associateBy { it.version }

    fun forVersion(version: Int): OfficialSnapshot? = BY_VERSION[version]
}

/**
 * 一个官方版本的完整结构。
 *
 * [identityHash] 是 Room 存在 `room_master_table` 里的 schema 指纹，可用来和实际库对照：
 * 版本号对得上但指纹不同，说明结构已经被改过，同样不能照快照读。
 */
internal class OfficialSnapshot(
    val version: Int,
    val identityHash: String,
    val tables: Map<String, OfficialSchemaTypes.Table>,
)
