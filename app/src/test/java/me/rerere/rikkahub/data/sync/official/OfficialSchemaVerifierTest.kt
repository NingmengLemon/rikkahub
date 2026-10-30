package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结构对账与类型判断的纯逻辑验证。读取层把 SQLite 查到的结果喂进来，这里验证判断本身。
 */
class OfficialSchemaVerifierTest {

    private fun allColumns() = OfficialDatabaseSchema.TABLES.mapValues { (_, table) ->
        table.columns.map { it.name }
    }

    @Test fun `accepts a snapshot shaped database`() {
        val verdict = OfficialSchemaVerifier.verify(OfficialDatabaseSchema.VERSION, allColumns())
        assertTrue("结构一致时应当通过，实际是 $verdict", verdict is OfficialSchemaVerifier.Verdict.Ok)
        assertTrue((verdict as OfficialSchemaVerifier.Verdict.Ok).unexpectedColumns.isEmpty())
    }

    @Test fun `reports columns the snapshot does not know but keeps going`() {
        val live = allColumns().toMutableMap().apply {
            this["workspaces"] = this.getValue("workspaces") + "future_column"
        }
        val verdict = OfficialSchemaVerifier.verify(OfficialDatabaseSchema.VERSION, live)
        assertTrue(verdict is OfficialSchemaVerifier.Verdict.Ok)
        assertEquals(
            listOf("future_column"),
            (verdict as OfficialSchemaVerifier.Verdict.Ok).unexpectedColumns["workspaces"],
        )
    }

    @Test fun `fails when a table is missing`() {
        val live = allColumns().toMutableMap().apply { remove("message_node") }
        val verdict = OfficialSchemaVerifier.verify(OfficialDatabaseSchema.VERSION, live)
        assertTrue(verdict is OfficialSchemaVerifier.Verdict.Failed)
        assertTrue((verdict as OfficialSchemaVerifier.Verdict.Failed).reason.contains("没有表 message_node"))
    }

    @Test fun `fails when a column is missing`() {
        val live = allColumns().toMutableMap().apply {
            this["workspaces"] = this.getValue("workspaces") - "root"
        }
        val verdict = OfficialSchemaVerifier.verify(OfficialDatabaseSchema.VERSION, live)
        assertTrue(verdict is OfficialSchemaVerifier.Verdict.Failed)
        assertTrue((verdict as OfficialSchemaVerifier.Verdict.Failed).reason.contains("缺少列 root"))
    }

    @Test fun `fails when the schema version differs`() {
        val verdict = OfficialSchemaVerifier.verify(OfficialDatabaseSchema.VERSION + 1, allColumns())
        assertTrue(verdict is OfficialSchemaVerifier.Verdict.Failed)
        assertTrue((verdict as OfficialSchemaVerifier.Verdict.Failed).reason.contains("版本"))
    }

    @Test fun `field type accepts the matching storage class only`() {
        assertTrue(OfficialFieldType.accepts("INTEGER", OfficialFieldType.INTEGER))
        assertTrue(OfficialFieldType.accepts("TEXT", OfficialFieldType.TEXT))
        assertTrue(OfficialFieldType.accepts("REAL", OfficialFieldType.REAL))
        assertTrue(OfficialFieldType.accepts("BLOB", OfficialFieldType.BLOB))
        // SQLite 会把 'abc' 写进 INTEGER 列，读的时候静默变成 0，这种必须拦住
        assertTrue(!OfficialFieldType.accepts("INTEGER", OfficialFieldType.TEXT))
        assertTrue(!OfficialFieldType.accepts("TEXT", OfficialFieldType.INTEGER))
        assertTrue(!OfficialFieldType.accepts("REAL", OfficialFieldType.INTEGER))
        assertTrue(!OfficialFieldType.accepts("BLOB", OfficialFieldType.TEXT))
        // NULL 由读取层单独短路，不走这里；未知 affinity 一律拒绝
        assertTrue(!OfficialFieldType.accepts("INTEGER", OfficialFieldType.NULL))
        assertTrue(!OfficialFieldType.accepts("NUMERIC", OfficialFieldType.INTEGER))
    }

    @Test fun `checks the version before any table`() {
        // 表全缺、版本也不对时，报的应当是版本：版本是分流的前提，先于结构
        val verdict = OfficialSchemaVerifier.verify(OfficialDatabaseSchema.VERSION + 1, emptyMap())
        assertTrue(verdict is OfficialSchemaVerifier.Verdict.Failed)
        assertTrue((verdict as OfficialSchemaVerifier.Verdict.Failed).reason.contains("版本"))
    }
}
