package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 附件引用重写的纯逻辑验证。
 *
 * 真实样本里这些引用的形状是 `file:///data/user/0/me.rerere.rikkahub/files/upload/...`，
 * 导入到改过包名的应用后必须换成当前安装的路径，否则 9092 处引用一起失效。
 */
class OfficialUrlRewriterTest {

    private val CURRENT = "file:///data/user/0/top.lemoneko.rakkihub/files/"

    @Test fun `rewrites the official package prefix`() {
        assertEquals(
            CURRENT + "upload/a.png",
            OfficialUrlRewriter.rewrite("file:///data/user/0/me.rerere.rikkahub/files/upload/a.png", CURRENT),
        )
    }

    @Test fun `rewrites an older fork package prefix`() {
        assertEquals(
            CURRENT + "upload/b.png",
            OfficialUrlRewriter.rewrite("file:///data/user/0/me.rerere.rikkahub.debug/files/upload/b.png", CURRENT),
        )
    }

    @Test fun `keeps relative and non file urls untouched`() {
        assertEquals("upload/c.png", OfficialUrlRewriter.rewrite("upload/c.png", CURRENT))
        assertEquals("https://example.com/d.png", OfficialUrlRewriter.rewrite("https://example.com/d.png", CURRENT))
        assertEquals(
            "content://media/external/images/1",
            OfficialUrlRewriter.rewrite("content://media/external/images/1", CURRENT),
        )
    }

    @Test fun `keeps a file url that is not under the files dir`() {
        // 外部存储或别的目录不属于应用私有 files，不动它
        val outside = "file:///data/user/0/me.rerere.rikkahub/databases/rikka_hub"
        assertEquals(outside, OfficialUrlRewriter.rewrite(outside, CURRENT))
    }

    @Test fun `rewrites every reference embedded in a json payload`() {
        val json = """[{"type":"image","url":"file:///data/user/0/me.rerere.rikkahub/files/upload/a.png"},""" +
            """{"type":"document","url":"file:///data/user/0/me.rerere.rikkahub/files/upload/b.pdf"}]"""
        val rewritten = OfficialUrlRewriter.rewriteEmbedded(json, CURRENT)
        assertEquals(
            """[{"type":"image","url":"${CURRENT}upload/a.png"},""" +
                """{"type":"document","url":"${CURRENT}upload/b.pdf"}]""",
            rewritten,
        )
    }

    @Test fun `leaves a json payload without file urls byte for byte`() {
        val json = """[{"type":"text","text":"聊天内容里提到 file:// 但不是绝对路径"}]"""
        assertEquals(json, OfficialUrlRewriter.rewriteEmbedded(json, CURRENT))
    }
}
