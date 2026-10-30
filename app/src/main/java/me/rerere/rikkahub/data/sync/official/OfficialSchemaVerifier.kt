package me.rerere.rikkahub.data.sync.official

/**
 * 官方库与冻结快照的结构对账，不依赖 Android。
 *
 * 拆出来是为了能在 JVM 上直接验证：读取层拿 `PRAGMA table_info` 的结果喂进来，这里只做判断。
 */
internal object OfficialSchemaVerifier {

    sealed interface Verdict {
        /** 结构对得上；[unexpectedColumns] 是快照之外的列，记日志后忽略 */
        data class Ok(val unexpectedColumns: Map<String, List<String>>) : Verdict

        data class Failed(val reason: String) : Verdict
    }

    /**
     * @param liveColumns 每张表实际的列名；缺表时该表不出现
     */
    fun verify(version: Int, liveColumns: Map<String, List<String>>): Verdict {
        if (version != OfficialDatabaseSchema.VERSION) {
            return Verdict.Failed("官方库版本 v$version 与快照 v${OfficialDatabaseSchema.VERSION} 不一致")
        }
        val unexpected = LinkedHashMap<String, List<String>>()
        for ((name, table) in OfficialDatabaseSchema.TABLES) {
            val actual = liveColumns[name] ?: return Verdict.Failed("官方库里没有表 $name")
            val expected = table.columns.map { it.name }
            val missing = expected.filter { it !in actual }
            if (missing.isNotEmpty()) {
                return Verdict.Failed("表 $name 缺少列 ${missing.joinToString()}")
            }
            val extra = actual.filter { it !in expected }
            if (extra.isNotEmpty()) unexpected[name] = extra
        }
        return Verdict.Ok(unexpected)
    }
}

/**
 * 行的实际存储类型与快照 affinity 的对应关系。
 *
 * SQLite 允许把任何值写进任何列，读的时候会静默强转（`'abc'` 读成 0、`'12xyz'` 读成 12）。
 * 导入是搬运，静默强转等于静默改数据，所以调用方要先用这里判断，对不上就失败。
 */
internal object OfficialFieldType {
    const val NULL = 0
    const val INTEGER = 1
    const val REAL = 2
    const val TEXT = 3
    const val BLOB = 4

    fun accepts(affinity: String, actualType: Int): Boolean = when (affinity.uppercase()) {
        "INTEGER" -> actualType == INTEGER
        "REAL" -> actualType == REAL
        "TEXT" -> actualType == TEXT
        "BLOB" -> actualType == BLOB
        else -> false
    }

    fun name(type: Int): String = when (type) {
        NULL -> "NULL"
        INTEGER -> "INTEGER"
        REAL -> "REAL"
        TEXT -> "TEXT"
        BLOB -> "BLOB"
        else -> "未知类型 $type"
    }
}
