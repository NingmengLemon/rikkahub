package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 写入顺序的纯逻辑验证。顺序错了外键会拒绝插入，所以这里按「父表必须排在子表之前」逐条检查。
 *
 * 除了对真实快照跑一遍，还用一份人为乱序的定义验证排序确实在按外键推，而不是照抄声明顺序。
 */
class OfficialTableOrderTest {

    @Test fun `every snapshot table appears exactly once`() {
        val order = OfficialTableOrder.insertionOrder()
        assertEquals(OfficialDatabaseSchema.TABLES.size, order.size)
        assertEquals(OfficialDatabaseSchema.TABLES.keys, order.toSet())
    }

    @Test fun `every parent table comes before the tables that reference it`() {
        val order = OfficialTableOrder.insertionOrder()
        val position = order.withIndex().associate { (index, name) -> name to index }
        for ((name, table) in OfficialDatabaseSchema.TABLES) {
            for (foreignKey in table.foreignKeys) {
                if (foreignKey.table !in OfficialDatabaseSchema.TABLES) continue
                assertTrue(
                    "表 $name 引用了 ${foreignKey.table}，后者必须排在前面",
                    position.getValue(foreignKey.table) < position.getValue(name),
                )
            }
        }
    }

    @Test fun `orders parents first even when they are declared later`() {
        // 声明顺序是 child → grandchild → parent，排序结果必须是依赖顺序
        val tables = linkedMapOf(
            "child" to table("child", foreignKeyTo = "parent"),
            "grandchild" to table("grandchild", foreignKeyTo = "child"),
            "parent" to table("parent"),
        )
        assertEquals(
            listOf("parent", "child", "grandchild"),
            OfficialTableOrder.insertionOrder(tables),
        )
    }

    @Test fun `reports circular foreign keys instead of looping forever`() {
        val tables = linkedMapOf(
            "a" to table("a", foreignKeyTo = "b"),
            "b" to table("b", foreignKeyTo = "a"),
        )
        val error = runCatching { OfficialTableOrder.insertionOrder(tables) }.exceptionOrNull()
        assertTrue("循环外键必须报错，实际是 $error", error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("循环外键"))
    }

    private fun table(name: String, foreignKeyTo: String? = null) = OfficialDatabaseSchema.Table(
        name = name,
        primaryKey = emptyList(),
        autoIncrement = false,
        columns = emptyList(),
        indices = emptyList(),
        foreignKeys = listOfNotNull(
            foreignKeyTo?.let {
                OfficialDatabaseSchema.ForeignKey(
                    table = it,
                    columns = listOf("parent_id"),
                    referencedColumns = listOf("id"),
                    onDelete = "CASCADE",
                    onUpdate = "NO ACTION",
                )
            }
        ),
    )
}
