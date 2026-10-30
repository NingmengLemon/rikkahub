package me.rerere.rikkahub.data.sync.official

/**
 * 官方 RikkaHub 数据库的结构快照。
 *
 * 这份快照是冻结的：它描述官方客户端导出的备份里有什么，不随本仓库的 Room schema
 * 演进。改它只有一个理由，就是官方升了版本；那时同步更新 [VERSION] 与表结构，并重跑
 * 导入侧与两个导出出口的样本。
 *
 * 数据来自 app/schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json，
 * `OfficialDatabaseSchemaTest` 会拿该文件对账，抄错或官方升级后忘了更新都会失败。
 */
internal object OfficialDatabaseSchema {
    const val VERSION = 32

    /** 官方 v32 的 Room schema identity，用来和官方库的 room_master_table 对照 */
    const val IDENTITY_HASH = "c64deeb13766eaf6528a2f4f6965c836"

    val tables: Map<String, Table> = listOf(
        Table(
            name = "ConversationEntity",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("assistant_id", "TEXT", notNull = true, defaultValue = "'0950e2dc-9bd5-4801-afa3-aa887aa36b4e'"),
                Column("title", "TEXT", notNull = true),
                Column("nodes", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
                Column("update_at", "INTEGER", notNull = true),
                Column("suggestions", "TEXT", notNull = true, defaultValue = "'[]'"),
                Column("is_pinned", "INTEGER", notNull = true, defaultValue = "0"),
                Column("custom_system_prompt", "TEXT", notNull = true, defaultValue = "''"),
                Column("mode_injection_ids", "TEXT", notNull = true, defaultValue = "'[]'"),
                Column("lorebook_ids", "TEXT", notNull = true, defaultValue = "'[]'"),
                Column("workspace_cwd", "TEXT", notNull = true, defaultValue = "''"),
                Column("folder_id", "TEXT", notNull = true, defaultValue = "''"),
                Column("config", "TEXT", notNull = true, defaultValue = "''"),
            ),
        ),
        Table(
            name = "memory_file",
            primaryKey = listOf("assistant_id", "path"),
            columns = listOf(
                Column("assistant_id", "TEXT", notNull = true),
                Column("path", "TEXT", notNull = true),
                Column("content", "TEXT", notNull = true),
                Column("updated_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "memory_consolidation",
            primaryKey = listOf("conversation_id"),
            columns = listOf(
                Column("conversation_id", "TEXT", notNull = true),
                Column("consolidated_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "GenMediaEntity",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "INTEGER", notNull = true),
                Column("path", "TEXT", notNull = true),
                Column("model_id", "TEXT", notNull = true),
                Column("prompt", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
                Column("type", "TEXT", notNull = true, defaultValue = "'image_generation'"),
                Column("source_paths", "TEXT", notNull = false),
            ),
        ),
        Table(
            name = "message_node",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("conversation_id", "TEXT", notNull = true),
                Column("node_index", "INTEGER", notNull = true),
                Column("messages", "TEXT", notNull = true),
                Column("select_index", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "managed_files",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "INTEGER", notNull = true),
                Column("folder", "TEXT", notNull = true),
                Column("relative_path", "TEXT", notNull = true),
                Column("display_name", "TEXT", notNull = true),
                Column("mime_type", "TEXT", notNull = true),
                Column("size_bytes", "INTEGER", notNull = true),
                Column("created_at", "INTEGER", notNull = true),
                Column("updated_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "favorites",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("type", "TEXT", notNull = true),
                Column("ref_key", "TEXT", notNull = true),
                Column("ref_json", "TEXT", notNull = true),
                Column("snapshot_json", "TEXT", notNull = true),
                Column("meta_json", "TEXT", notNull = false),
                Column("created_at", "INTEGER", notNull = true),
                Column("updated_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "workspaces",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("name", "TEXT", notNull = true),
                Column("root", "TEXT", notNull = true),
                Column("shell_status", "TEXT", notNull = true),
                Column("created_at", "INTEGER", notNull = true),
                Column("updated_at", "INTEGER", notNull = true),
                Column("last_access_at", "INTEGER", notNull = false),
                Column("tool_approvals", "TEXT", notNull = true, defaultValue = "'{}'"),
                Column("shell_compatibility_mode", "INTEGER", notNull = true, defaultValue = "0"),
                Column("type", "TEXT", notNull = true, defaultValue = "'LOCAL'"),
                Column("ssh_config", "TEXT", notNull = false),
            ),
        ),
        Table(
            name = "conversation_folder",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("assistant_id", "TEXT", notNull = true),
                Column("name", "TEXT", notNull = true),
                Column("sort_index", "INTEGER", notNull = true, defaultValue = "0"),
                Column("create_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "media_creation_session",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("title", "TEXT", notNull = true),
                Column("draft", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
                Column("update_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "media_creation_node",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("session_id", "TEXT", notNull = true),
                Column("selected_record_id", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "media_creation_record",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("session_id", "TEXT", notNull = true),
                Column("node_id", "TEXT", notNull = true),
                Column("provider_id", "TEXT", notNull = true),
                Column("provider_name", "TEXT", notNull = true),
                Column("model_id", "TEXT", notNull = true),
                Column("kind", "TEXT", notNull = true),
                Column("prompt", "TEXT", notNull = true),
                Column("params", "TEXT", notNull = true),
                Column("inputs", "TEXT", notNull = true),
                Column("status", "TEXT", notNull = true),
                Column("task_id", "TEXT", notNull = false),
                Column("error", "TEXT", notNull = false),
                Column("outputs", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
                Column("update_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "translation_history",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("source_text", "TEXT", notNull = true),
                Column("translated_text", "TEXT", notNull = true),
                Column("target_language", "TEXT", notNull = true),
                Column("model_id", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
            ),
        ),
        Table(
            name = "usage_record",
            primaryKey = listOf("id"),
            columns = listOf(
                Column("id", "INTEGER", notNull = true),
                Column("created_at", "INTEGER", notNull = true),
                Column("source", "TEXT", notNull = true),
                Column("provider_id", "TEXT", notNull = false),
                Column("model_id", "TEXT", notNull = false),
                Column("model_name", "TEXT", notNull = false),
                Column("assistant_id", "TEXT", notNull = false),
                Column("conversation_id", "TEXT", notNull = false),
                Column("prompt_tokens", "INTEGER", notNull = true),
                Column("completion_tokens", "INTEGER", notNull = true),
                Column("cached_tokens", "INTEGER", notNull = true),
            ),
        ),
    ).associateBy { it.name }

    val tableNames: Set<String> get() = tables.keys

    data class Table(
        val name: String,
        val primaryKey: List<String>,
        val columns: List<Column>,
    ) {
        val columnNames: Set<String> = columns.mapTo(LinkedHashSet()) { it.name }
    }

    data class Column(
        val name: String,
        val affinity: String,
        val notNull: Boolean,
        val defaultValue: String? = null,
    )
}
