package me.rerere.rikkahub.data.db.fts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重建判据的验证。
 *
 * 判错的两种方向都不好：该重建没重建，搜索永久缺内容；不该重建却重建，每次启动都白扫一遍
 * 整个库（重建开头就是清空，耗时且期间搜索不可用）。
 */
class SearchIndexHealthTest {

    @Test fun `conversations with an empty index must be rebuilt`() {
        assertTrue(SearchIndexHealth.needsRebuild(conversationCount = 12, indexIsEmpty = true))
    }

    @Test fun `a populated index needs no rebuild`() {
        assertFalse(SearchIndexHealth.needsRebuild(conversationCount = 12, indexIsEmpty = false))
    }

    @Test fun `an empty database needs no rebuild`() {
        // 全新安装就是这样：没有会话，索引空是正常的
        assertFalse(SearchIndexHealth.needsRebuild(conversationCount = 0, indexIsEmpty = true))
    }

    @Test fun `empty database with an empty index is still nothing to do`() {
        assertFalse(SearchIndexHealth.needsRebuild(conversationCount = 0, indexIsEmpty = false))
    }

    @Test fun `a single conversation is enough to trigger`() {
        assertTrue(SearchIndexHealth.needsRebuild(conversationCount = 1, indexIsEmpty = true))
    }
}
