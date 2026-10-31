package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 写入顺序的纯逻辑验证。顺序错了外键会拒绝插入，所以这里按「父表必须排在子表之前」逐条检查。
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

    @Test fun `message node comes after its conversation`() {
        val order = OfficialTableOrder.insertionOrder()
        assertTrue(order.indexOf("message_node") > order.indexOf("ConversationEntity"))
    }
}
