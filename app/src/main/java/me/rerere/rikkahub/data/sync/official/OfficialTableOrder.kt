package me.rerere.rikkahub.data.sync.official

/**
 * 官方表的写入顺序：父表先于子表。
 *
 * 数据库开了外键约束，顺序错了插入会直接失败。顺序从快照的外键定义推出来，不手写：官方
 * 升版新增外键时这里跟着快照走。当前的快照恰好已经按依赖关系声明，所以输出看起来与声明
 * 顺序一致，但推的是依赖而不是位置。
 *
 * 不依赖 Android，可以直接跑 JVM 测试。
 */
internal object OfficialTableOrder {

    /**
     * @param tables 默认是冻结快照；测试可以传一份人为乱序的定义，验证排序确实在按外键推
     */
    fun insertionOrder(
        tables: Map<String, OfficialDatabaseSchema.Table> = OfficialDatabaseSchema.TABLES,
    ): List<String> {
        val ordered = ArrayList<String>(tables.size)
        val visiting = HashSet<String>()
        val done = HashSet<String>()

        fun visit(name: String) {
            if (name in done) return
            // 官方若出现循环外键，这里会报出来而不是死循环
            check(visiting.add(name)) { "官方表之间存在循环外键：$name" }
            val table = tables.getValue(name)
            for (foreignKey in table.foreignKeys) {
                if (foreignKey.table in tables) visit(foreignKey.table)
            }
            visiting.remove(name)
            done.add(name)
            ordered.add(name)
        }

        tables.keys.forEach(::visit)
        return ordered
    }
}
