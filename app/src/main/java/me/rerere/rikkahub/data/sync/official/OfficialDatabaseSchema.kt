package me.rerere.rikkahub.data.sync.official

/**
 * 本仓库 Room schema 当前对应的官方版本。
 *
 * 表定义本身在各版本的快照里（[OfficialSnapshotV31]、[OfficialSnapshotV32]），由
 * [OfficialSnapshots] 注册。这里保留 [VERSION] 与 [IDENTITY_HASH] 是因为两处要用：
 * 官方兼容导出要知道「我们的库现在对着哪一版」，`OfficialDatabaseSchemaTest` 要断言仓库里
 * 最高的 Room schema 版本与它一致。
 *
 * [TABLES] 是 v32 的别名，留给既有引用；新代码按版本从 [OfficialSnapshots] 取。
 */
internal object OfficialDatabaseSchema {
    const val VERSION = 32

    /** 官方 v32 的 Room schema identity，可用来和官方库的 room_master_table 对照 */
    const val IDENTITY_HASH = "c64deeb13766eaf6528a2f4f6965c836"

    val TABLES: Map<String, OfficialSchemaTypes.Table> get() = OfficialSnapshotV32.TABLES
}
