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
 * 这条测试同时守两件事：抄错快照，以及官方升级后忘了更新快照。它读取的是
 * `app/schemas/.../32.json`，Gradle 跑单测时的工作目录就是 app 模块根目录。
 */
class OfficialDatabaseSchemaTest {

    private val schemaFile = File("schemas/me.rerere.rikkahub.data.db.AppDatabase/32.json")

    @Test
    fun `schema file is reachable from the test working directory`() {
        assertTrue("找不到 ${schemaFile.absolutePath}", schemaFile.isFile)
    }

    @Test
    fun `snapshot matches the checked-in room schema`() {
        val database = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        assertEquals(OfficialDatabaseSchema.VERSION, database.getValue("version").jsonPrimitive.int)
        assertEquals(OfficialDatabaseSchema.IDENTITY_HASH, database.getValue("identityHash").jsonPrimitive.content)

        val entities = database.getValue("entities").jsonArray.map { it.jsonObject }
        assertEquals(
            entities.map { it.getValue("tableName").jsonPrimitive.content }.toSet(),
            OfficialDatabaseSchema.tableNames,
        )

        entities.forEach { entity ->
            val tableName = entity.getValue("tableName").jsonPrimitive.content
            val table = requireNotNull(OfficialDatabaseSchema.tables[tableName]) { "快照缺少表 $tableName" }
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

            val primaryKey = entity.getValue("primaryKey").jsonObject
                .getValue("columnNames").jsonArray
                .map { it.jsonPrimitive.content }
            assertEquals("表 $tableName 的主键不一致", primaryKey, table.primaryKey)
        }
    }
}
