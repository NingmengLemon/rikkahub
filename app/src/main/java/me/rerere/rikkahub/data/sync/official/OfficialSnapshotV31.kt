package me.rerere.rikkahub.data.sync.official

/**
 * 官方 v31 的数据库结构快照，从 app/schemas/.../31.json 生成。
 *
 * 由 tools 脚本产出，不要手改：官方升版时重新生成，与 [OfficialDatabaseSchema] 一起由
 * [OfficialSnapshots] 注册，读取层按库里的 `user_version` 选对应的这一份。
 */
internal object OfficialSnapshotV31 {
    const val VERSION = 31

    const val IDENTITY_HASH = "a428043e3256791d9bc0855715c6355a"

    val TABLES: Map<String, OfficialSchemaTypes.Table> = listOf(
        OfficialSchemaTypes.Table(
            name = "ConversationEntity",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column(
                    "assistant_id",
                    "TEXT",
                    notNull = true,
                    defaultValue = "'0950e2dc-9bd5-4801-afa3-aa887aa36b4e'"
                ),
                OfficialSchemaTypes.Column("title", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("nodes", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("create_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("update_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("suggestions", "TEXT", notNull = true, defaultValue = "'[]'"),
                OfficialSchemaTypes.Column("is_pinned", "INTEGER", notNull = true, defaultValue = "0"),
                OfficialSchemaTypes.Column("custom_system_prompt", "TEXT", notNull = true, defaultValue = "''"),
                OfficialSchemaTypes.Column("mode_injection_ids", "TEXT", notNull = true, defaultValue = "'[]'"),
                OfficialSchemaTypes.Column("lorebook_ids", "TEXT", notNull = true, defaultValue = "'[]'"),
                OfficialSchemaTypes.Column("workspace_cwd", "TEXT", notNull = true, defaultValue = "''"),
                OfficialSchemaTypes.Column("folder_id", "TEXT", notNull = true, defaultValue = "''"),
                OfficialSchemaTypes.Column("config", "TEXT", notNull = true, defaultValue = "''"),
            ),
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "memory_file",
            primaryKey = listOf("assistant_id", "path"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("assistant_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("path", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("content", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("updated_at", "INTEGER", notNull = true),
            ),
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "memory_consolidation",
            primaryKey = listOf("conversation_id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("conversation_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("consolidated_at", "INTEGER", notNull = true),
            ),
            indices = listOf(),
            foreignKeys = listOf(
                OfficialSchemaTypes.ForeignKey(
                    table = "ConversationEntity",
                    columns = listOf("conversation_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE",
                    onUpdate = "NO ACTION"
                ),
            ),
        ),
        OfficialSchemaTypes.Table(
            name = "GenMediaEntity",
            primaryKey = listOf("id"),
            autoIncrement = true,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("path", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("model_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("prompt", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("create_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("type", "TEXT", notNull = true, defaultValue = "'image_generation'"),
                OfficialSchemaTypes.Column("source_paths", "TEXT", notNull = false),
            ),
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "message_node",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("conversation_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("node_index", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("messages", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("select_index", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_message_node_conversation_id",
                    unique = false,
                    columns = listOf("conversation_id"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(
                OfficialSchemaTypes.ForeignKey(
                    table = "ConversationEntity",
                    columns = listOf("conversation_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE",
                    onUpdate = "NO ACTION"
                ),
            ),
        ),
        OfficialSchemaTypes.Table(
            name = "managed_files",
            primaryKey = listOf("id"),
            autoIncrement = true,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("folder", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("relative_path", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("display_name", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("mime_type", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("size_bytes", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("created_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("updated_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_managed_files_relative_path",
                    unique = true,
                    columns = listOf("relative_path"),
                    orders = listOf()
                ),
                OfficialSchemaTypes.Index(
                    "index_managed_files_folder",
                    unique = false,
                    columns = listOf("folder"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "favorites",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("type", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("ref_key", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("ref_json", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("snapshot_json", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("meta_json", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("created_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("updated_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_favorites_ref_key",
                    unique = true,
                    columns = listOf("ref_key"),
                    orders = listOf()
                ),
                OfficialSchemaTypes.Index(
                    "index_favorites_type",
                    unique = false,
                    columns = listOf("type"),
                    orders = listOf()
                ),
                OfficialSchemaTypes.Index(
                    "index_favorites_created_at",
                    unique = false,
                    columns = listOf("created_at"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "workspaces",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("name", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("root", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("shell_status", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("created_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("updated_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("last_access_at", "INTEGER", notNull = false),
                OfficialSchemaTypes.Column("tool_approvals", "TEXT", notNull = true, defaultValue = "'{}'"),
                OfficialSchemaTypes.Column("shell_compatibility_mode", "INTEGER", notNull = true, defaultValue = "0"),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_workspaces_root",
                    unique = true,
                    columns = listOf("root"),
                    orders = listOf()
                ),
                OfficialSchemaTypes.Index(
                    "index_workspaces_updated_at",
                    unique = false,
                    columns = listOf("updated_at"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "conversation_folder",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("assistant_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("name", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("sort_index", "INTEGER", notNull = true, defaultValue = "0"),
                OfficialSchemaTypes.Column("create_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_conversation_folder_assistant_id",
                    unique = false,
                    columns = listOf("assistant_id"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "media_creation_session",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("title", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("draft", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("create_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("update_at", "INTEGER", notNull = true),
            ),
            indices = listOf(),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "media_creation_node",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("session_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("selected_record_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("create_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_media_creation_node_session_id",
                    unique = false,
                    columns = listOf("session_id"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(
                OfficialSchemaTypes.ForeignKey(
                    table = "media_creation_session",
                    columns = listOf("session_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE",
                    onUpdate = "NO ACTION"
                ),
            ),
        ),
        OfficialSchemaTypes.Table(
            name = "media_creation_record",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("session_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("node_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("provider_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("provider_name", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("model_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("kind", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("prompt", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("params", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("inputs", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("status", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("task_id", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("error", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("outputs", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("create_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("update_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_media_creation_record_session_id",
                    unique = false,
                    columns = listOf("session_id"),
                    orders = listOf()
                ),
                OfficialSchemaTypes.Index(
                    "index_media_creation_record_node_id",
                    unique = false,
                    columns = listOf("node_id"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(
                OfficialSchemaTypes.ForeignKey(
                    table = "media_creation_session",
                    columns = listOf("session_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE",
                    onUpdate = "NO ACTION"
                ),
                OfficialSchemaTypes.ForeignKey(
                    table = "media_creation_node",
                    columns = listOf("node_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE",
                    onUpdate = "NO ACTION"
                ),
            ),
        ),
        OfficialSchemaTypes.Table(
            name = "translation_history",
            primaryKey = listOf("id"),
            autoIncrement = false,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("source_text", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("translated_text", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("target_language", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("model_id", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("create_at", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_translation_history_create_at",
                    unique = false,
                    columns = listOf("create_at"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(),
        ),
        OfficialSchemaTypes.Table(
            name = "usage_record",
            primaryKey = listOf("id"),
            autoIncrement = true,
            columns = listOf(
                OfficialSchemaTypes.Column("id", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("created_at", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("source", "TEXT", notNull = true),
                OfficialSchemaTypes.Column("provider_id", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("model_id", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("model_name", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("assistant_id", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("conversation_id", "TEXT", notNull = false),
                OfficialSchemaTypes.Column("prompt_tokens", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("completion_tokens", "INTEGER", notNull = true),
                OfficialSchemaTypes.Column("cached_tokens", "INTEGER", notNull = true),
            ),
            indices = listOf(
                OfficialSchemaTypes.Index(
                    "index_usage_record_created_at",
                    unique = false,
                    columns = listOf("created_at"),
                    orders = listOf()
                ),
            ),
            foreignKeys = listOf(),
        ),
    ).associateBy { it.name }
}
