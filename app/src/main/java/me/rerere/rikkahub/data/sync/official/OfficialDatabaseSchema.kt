package me.rerere.rikkahub.data.sync.official

/**
 * 官方 RikkaHub 数据库的结构快照。

 * 这份快照是冻结的：它描述官方客户端导出的备份里有什么，不随本仓库的 Room schema
 * 演进。它将来要服务两件事，目前都还没实现：导入器按它用裸 SQL 读官方库，官方兼容
 * 导出按它把我们的库重建成官方形状。
 *
 * 修它只有一个理由，就是官方升了版本：同步更新 [VERSION] 与下面的结构，并重跑
 * `OfficialDatabaseSchemaTest`。自家 schema 一旦与官方分叉，版本判定的那条断言要重做。
 *
 * 数据来自 app/schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json。官方库里还有
 * `message_fts` 这类由手写 SQL 建的全文索引表，它们不在 Room 的 schema 里，也不在这份
 * 快照里：那是派生数据，`AppDatabaseFactory` 每次打开都会重建，导入时忽略即可。
 */
internal object OfficialDatabaseSchema {
    const val VERSION = 32

    /** 官方 v32 的 Room schema identity，可用来和官方库的 room_master_table 对照 */
    const val IDENTITY_HASH = "c64deeb13766eaf6528a2f4f6965c836"

    val TABLES: Map<String, Table> = listOf(
        Table(
            name = "ConversationEntity",
            primaryKey = listOf("id"),
            autoIncrement = false,
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
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        Table(
            name = "memory_file",
            primaryKey = listOf("assistant_id", "path"),
            autoIncrement = false,
            columns = listOf(
                Column("assistant_id", "TEXT", notNull = true),
                Column("path", "TEXT", notNull = true),
                Column("content", "TEXT", notNull = true),
                Column("updated_at", "INTEGER", notNull = true),
            ),
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        Table(
            name = "memory_consolidation",
            primaryKey = listOf("conversation_id"),
            autoIncrement = false,
            columns = listOf(
                Column("conversation_id", "TEXT", notNull = true),
                Column("consolidated_at", "INTEGER", notNull = true),
            ),
            indices = listOf(),
            foreignKeys = listOf(
                ForeignKey(
                    table = "ConversationEntity",
                    columns = listOf("conversation_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE"
                ),
            ),
        ),
        Table(
            name = "GenMediaEntity",
            primaryKey = listOf("id"),
            autoIncrement = true,
            columns = listOf(
                Column("id", "INTEGER", notNull = true),
                Column("path", "TEXT", notNull = true),
                Column("model_id", "TEXT", notNull = true),
                Column("prompt", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
                Column("type", "TEXT", notNull = true, defaultValue = "'image_generation'"),
                Column("source_paths", "TEXT", notNull = false),
            ),
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        Table(
            name = "message_node",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("conversation_id", "TEXT", notNull = true),
                Column("node_index", "INTEGER", notNull = true),
                Column("messages", "TEXT", notNull = true),
                Column("select_index", "INTEGER", notNull = true),
            ),
            indices = listOf(
                Index("index_message_node_conversation_id", unique = false, columns = listOf("conversation_id")),
            ),
            foreignKeys = listOf(
                ForeignKey(
                    table = "ConversationEntity",
                    columns = listOf("conversation_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE"
                ),
            ),
        ),
        Table(
            name = "managed_files",
            primaryKey = listOf("id"),
            autoIncrement = true,
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
            indices = listOf(
                Index("index_managed_files_relative_path", unique = true, columns = listOf("relative_path")),
                Index("index_managed_files_folder", unique = false, columns = listOf("folder")),
            ),
            foreignKeys = listOf(),
        ),
        Table(
            name = "favorites",
            primaryKey = listOf("id"),
            autoIncrement = false,
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
            indices = listOf(
                Index("index_favorites_ref_key", unique = true, columns = listOf("ref_key")),
                Index("index_favorites_type", unique = false, columns = listOf("type")),
                Index("index_favorites_created_at", unique = false, columns = listOf("created_at")),
            ),
            foreignKeys = listOf(),
        ),
        Table(
            name = "workspaces",
            primaryKey = listOf("id"),
            autoIncrement = false,
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
            indices = listOf(
                Index("index_workspaces_root", unique = true, columns = listOf("root")),
                Index("index_workspaces_updated_at", unique = false, columns = listOf("updated_at")),
            ),
            foreignKeys = listOf(),
        ),
        Table(
            name = "conversation_folder",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("assistant_id", "TEXT", notNull = true),
                Column("name", "TEXT", notNull = true),
                Column("sort_index", "INTEGER", notNull = true, defaultValue = "0"),
                Column("create_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                Index("index_conversation_folder_assistant_id", unique = false, columns = listOf("assistant_id")),
            ),
            foreignKeys = listOf(),
        ),
        Table(
            name = "media_creation_session",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("title", "TEXT", notNull = true),
                Column("draft", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
                Column("update_at", "INTEGER", notNull = true),
            ),
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        Table(
            name = "media_creation_node",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("session_id", "TEXT", notNull = true),
                Column("selected_record_id", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                Index("index_media_creation_node_session_id", unique = false, columns = listOf("session_id")),
            ),
            foreignKeys = listOf(
                ForeignKey(
                    table = "media_creation_session",
                    columns = listOf("session_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE"
                ),
            ),
        ),
        Table(
            name = "media_creation_record",
            primaryKey = listOf("id"),
            autoIncrement = false,
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
            indices = listOf(
                Index("index_media_creation_record_session_id", unique = false, columns = listOf("session_id")),
                Index("index_media_creation_record_node_id", unique = false, columns = listOf("node_id")),
            ),
            foreignKeys = listOf(
                ForeignKey(
                    table = "media_creation_session",
                    columns = listOf("session_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE"
                ),
                ForeignKey(
                    table = "media_creation_node",
                    columns = listOf("node_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE"
                ),
            ),
        ),
        Table(
            name = "translation_history",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                Column("id", "TEXT", notNull = true),
                Column("source_text", "TEXT", notNull = true),
                Column("translated_text", "TEXT", notNull = true),
                Column("target_language", "TEXT", notNull = true),
                Column("model_id", "TEXT", notNull = true),
                Column("create_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                Index("index_translation_history_create_at", unique = false, columns = listOf("create_at")),
            ),
            foreignKeys = listOf(),
        ),
        Table(
            name = "usage_record",
            primaryKey = listOf("id"),
            autoIncrement = true,
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
            indices = listOf(
                Index("index_usage_record_created_at", unique = false, columns = listOf("created_at")),
            ),
            foreignKeys = listOf(),
        ),
    ).associateBy { it.name }

    data class Table(
        val name: String,
        val primaryKey: List<String>,
        val autoIncrement: Boolean,
        val columns: List<Column>,
        val indices: List<Index>,
        val foreignKeys: List<ForeignKey>,
    )

    data class Column(
        val name: String,
        val affinity: String,
        val notNull: Boolean,
        val defaultValue: String? = null,
    )

    data class Index(
        val name: String,
        val unique: Boolean,
        val columns: List<String>,
    )

    data class ForeignKey(
        val table: String,
        val columns: List<String>,
        val referencedColumns: List<String>,
        val onDelete: String,
    )
}
