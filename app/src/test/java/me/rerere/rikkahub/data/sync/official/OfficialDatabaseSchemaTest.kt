package me.rerere.rikkahub.data.sync.official

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 把冻结的快照和 Room 自己产出的 schema 文件对账。
 *
 * 这条测试守两件事：快照抄错，以及官方升级后忘了更新快照。Gradle 跑单测时的工作目录
 * 是 app 模块根目录，所以 `schemas/...` 能直接读到。
 */
class OfficialDatabaseSchemaTest {

    private val schemaDir = File("schemas/me.rerere.rikkahub.data.db.AppDatabase")
    private val schemaFile = File(schemaDir, "${OfficialDatabaseSchema.VERSION}.json")

    @Test
    fun `repo schema version matches the snapshot version`() {
        assertTrue("找不到 ${schemaDir.absolutePath}", schemaDir.isDirectory)

        val highest = schemaDir.listFiles()
            ?.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            ?.maxOrNull()
            ?: throw AssertionError("${schemaDir.absolutePath} 里没有可识别的版本文件")

        // 两条分开报，读的人才知道该往哪个方向查：快照超前说明抄错了来源，落后说明官方升了版本。
        if (highest < OfficialDatabaseSchema.VERSION) {
            throw AssertionError(
                "快照版本 v${OfficialDatabaseSchema.VERSION} 高于仓库里的 Room schema v$highest，" +
                    "快照可能对着更新的官方版本写的。检查 schema 来源。"
            )
        }
        if (highest > OfficialDatabaseSchema.VERSION) {
            throw AssertionError(
                "仓库里的 Room schema 已经到 v$highest，快照还停在 v${OfficialDatabaseSchema.VERSION}。" +
                    "官方升级时同步更新快照；如果是我们自己升的版本，这条断言要改成对着官方来源校验，" +
                    "而不是对着 app/schemas。"
            )
        }
    }

    @Test
    fun `snapshot matches the checked-in room schema`() {
        val database = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        assertEquals(OfficialDatabaseSchema.VERSION, database.getValue("version").jsonPrimitive.int)
        assertEquals(OfficialDatabaseSchema.IDENTITY_HASH, database.getValue("identityHash").jsonPrimitive.content)

        val entities = database.getValue("entities").jsonArray.map { it.jsonObject }
        assertEquals(
            entities.map { it.getValue("tableName").jsonPrimitive.content }.toSet(),
            OfficialDatabaseSchema.TABLES.keys,
        )

        entities.forEach { entity ->
            val tableName = entity.getValue("tableName").jsonPrimitive.content
            val table = requireNotNull(OfficialDatabaseSchema.TABLES[tableName]) { "快照缺少表 $tableName" }

            val primaryKey = entity.getValue("primaryKey").jsonObject
            assertEquals(
                "表 $tableName 的主键不一致",
                primaryKey.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content },
                table.primaryKey,
            )
            assertEquals(
                "表 $tableName 的自增标记不一致",
                primaryKey.getValue("autoGenerate").jsonPrimitive.boolean,
                table.autoIncrement,
            )

            val fields = entity.getValue("fields").jsonArray.map { it.jsonObject }
            assertEquals(
                "表 $tableName 的列不一致",
                fields.map { it.getValue("columnName").jsonPrimitive.content },
                table.columns.map { it.name },
            )
            fields.zip(table.columns).forEach { (json, column) ->
                assertEquals(json.getValue("affinity").jsonPrimitive.content, column.affinity)
                // Room 对可空列省略 notNull 字段，而不是写成 false
                assertEquals(json["notNull"]?.jsonPrimitive?.boolean ?: false, column.notNull)
                assertEquals(json["defaultValue"]?.jsonPrimitive?.content, column.defaultValue)
            }

            val indices = entity["indices"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            assertEquals("表 $tableName 的索引数量不一致", indices.size, table.indices.size)
            indices.zip(table.indices).forEach { (json, index) ->
                assertEquals(json.getValue("name").jsonPrimitive.content, index.name)
                assertEquals(json.getValue("unique").jsonPrimitive.boolean, index.unique)
                assertEquals(
                    json.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content },
                    index.columns,
                )
                assertEquals(
                    json["orders"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    index.orders,
                )
            }

            val foreignKeys = entity["foreignKeys"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            assertEquals("表 $tableName 的外键数量不一致", foreignKeys.size, table.foreignKeys.size)
            foreignKeys.zip(table.foreignKeys).forEach { (json, foreignKey) ->
                assertEquals(json.getValue("table").jsonPrimitive.content, foreignKey.table)
                assertEquals(
                    json.getValue("columns").jsonArray.map { it.jsonPrimitive.content },
                    foreignKey.columns,
                )
                assertEquals(
                    json.getValue("referencedColumns").jsonArray.map { it.jsonPrimitive.content },
                    foreignKey.referencedColumns,
                )
                assertEquals(json.getValue("onDelete").jsonPrimitive.content, foreignKey.onDelete)
                assertEquals(json.getValue("onUpdate").jsonPrimitive.content, foreignKey.onUpdate)
            }
        }
    }
}
