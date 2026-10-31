package me.rerere.rikkahub.data.db.fts

/**
 * 启动时要不要重建全文索引。
 *
 * 判据拆成纯函数是因为它守着一个容易被忽略的失败：索引是派生数据，备份恢复搬不过来，全量
 * 重建的第一步又是清空，所以「恢复被中断」与「重建中途被杀」留下的是同一个状态——空索引。
 * 用户只会觉得搜索搜不到，不会想到去重建。
 */
internal object SearchIndexHealth {

    /**
     * @param conversationCount 会话表里的条数
     * @param indexIsEmpty 索引里有没有内容
     */
    fun needsRebuild(conversationCount: Int, indexIsEmpty: Boolean): Boolean =
        conversationCount > 0 && indexIsEmpty
}
