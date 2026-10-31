package me.rerere.rikkahub.data.sync.official

/**
 * 官方数据库结构的类型定义。
 *
 * 抽在这里是为了让各版本的快照（[OfficialSnapshotV31]、[OfficialSnapshotV32]）与注册表
 * [OfficialSnapshots] 不必互相引用：快照只依赖这些数据类，注册表只依赖快照。
 */
internal object OfficialSchemaTypes {

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
        val orders: List<String>,
    )

    data class ForeignKey(
        val table: String,
        val columns: List<String>,
        val referencedColumns: List<String>,
        val onDelete: String,
        val onUpdate: String,
    )
}
