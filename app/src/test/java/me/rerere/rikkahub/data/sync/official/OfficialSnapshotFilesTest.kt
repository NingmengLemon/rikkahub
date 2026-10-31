package me.rerere.rikkahub.data.sync.official

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 每份官方快照都要与它自己那一版的 Room schema 文件对账。
 *
 * 只对最新版本对账不够：注册表里的旧版本（现在只有 v31）写坏了不会有任何测试报出来，而它正是
 * 读旧备份的依据。
 */
class OfficialSnapshotFilesTest {

    private val schemaDir = File("schemas/me.rerere.rikkahub.data.db.AppDatabase")

    @Test fun `every registered snapshot matches its room schema file`() {
        for ((version, snapshot) in OfficialSnapshots.BY_VERSION) {
            val file = File(schemaDir, "$version.json")
            val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
            assertEquals(
                "v$version 的 identity 与 schema 文件不一致",
                database.getValue("identityHash").jsonPrimitive.content,
                snapshot.identityHash,
            )
            val entities = database.getValue("entities").jsonArray.map { it.jsonObject }
            assertEquals(
                "v$version 的表集合与 schema 文件不一致",
                entities.map { it.getValue("tableName").jsonPrimitive.content }.toSet(),
                snapshot.tables.keys,
            )
            entities.forEach { entity ->
                val name = entity.getValue("tableName").jsonPrimitive.content
                val table = requireNotNull(snapshot.tables[name]) { "v$version 快照缺少表 $name" }
                val fields = entity.getValue("fields").jsonArray.map { it.jsonObject }
                assertEquals(
                    "v$version 表 $name 的列不一致",
                    fields.map { it.getValue("columnName").jsonPrimitive.content },
                    table.columns.map { it.name },
                )
                fields.zip(table.columns).forEach { (json, column) ->
                    assertEquals(
                        "v$version $name.${column.name} 的 affinity",
                        json.getValue("affinity").jsonPrimitive.content,
                        column.affinity
                    )
                    assertEquals(
                        "v$version $name.${column.name} 的可空性",
                        json["notNull"]?.jsonPrimitive?.boolean ?: false,
                        column.notNull
                    )
                }
                val primaryKey = entity.getValue("primaryKey").jsonObject
                assertEquals(
                    "v$version 表 $name 的主键",
                    primaryKey.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content },
                    table.primaryKey,
                )
                assertEquals(
                    "v$version 表 $name 的自增标记",
                    primaryKey.getValue("autoGenerate").jsonPrimitive.boolean,
                    table.autoIncrement,
                )
            }
        }
    }

    @Test fun `the registered versions are exactly the ones with schema files we ship`() {
        // 注册多了会去读不存在的文件，注册少了会让能恢复的备份被拒
        for (version in OfficialSnapshots.BY_VERSION.keys) {
            assertEquals(
                "缺少 v$version 的 schema 文件",
                true,
                File(schemaDir, "$version.json").isFile,
            )
        }
    }
}
