package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 清理判定的验证。
 *
 * 判错的方向只有一个不能接受：把还在被引用的文件删掉。所以这里除了「孤儿被选中」，还要
 * 钉住「引用的不被选中」「引用形状认不出来时宁可不当孤儿」。
 */
class FilesCleanupPlannerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `files nobody references are selected`() {
        val orphans = FilesCleanupPlanner.orphaned(
            referenced = setOf("upload/a.png"),
            candidates = listOf("upload/a.png", "upload/b.png", "upload/c.png"),
        )
        assertEquals(listOf("upload/b.png", "upload/c.png"), orphans)
    }

    @Test fun `referenced files are never selected`() {
        val orphans = FilesCleanupPlanner.orphaned(
            referenced = setOf("upload/a.png", "upload/b.png"),
            candidates = listOf("upload/a.png", "upload/b.png"),
        )
        assertTrue(orphans.isEmpty())
    }

    @Test fun `the result is sorted for stable reporting`() {
        val orphans = FilesCleanupPlanner.orphaned(
            referenced = emptySet(),
            candidates = listOf("upload/c.png", "upload/a.png", "upload/b.png"),
        )
        assertEquals(listOf("upload/a.png", "upload/b.png", "upload/c.png"), orphans)
    }

    @Test fun `absolute file urls are reduced to relative paths`() {
        val json = """[{"type":"image","url":"file:///data/user/0/me.rerere.rikkahub/files/upload/abc.png"},""" +
            """{"type":"document","url":"file:///data/data/top.lemoneko.rikkihub/files/upload/def.pdf"}]"""
        assertEquals(
            listOf("upload/abc.png", "upload/def.pdf"),
            FilesCleanupPlanner.referencedPathsIn(json),
        )
    }

    @Test fun `references outside the upload folder are ignored`() {
        // 技能、字体、媒体创作各有自己的生命周期，这里不动
        val json = """{"url":"file:///data/user/0/pkg/files/skills/demo/SKILL.md"}"""
        assertTrue(FilesCleanupPlanner.referencedPathsIn(json).isEmpty())
    }

    @Test fun `content without file urls yields nothing`() {
        assertTrue(FilesCleanupPlanner.referencedPathsIn("""[{"type":"text","text":"hi"}]""").isEmpty())
        assertTrue(FilesCleanupPlanner.referencedPathsIn("").isEmpty())
    }

    @Test fun `absolute urls of another app still count as the same relative path`() {
        // 导入之后引用会被重写成本机路径，但清理要能跨包名认出来
        val json = """{"url":"file:///data/user/0/whatever/files/upload/x.png"}"""
        assertEquals(listOf("upload/x.png"), FilesCleanupPlanner.referencedPathsIn(json))
    }

    @Test fun `candidates are collected recursively from the upload folder`() {
        val root = temporary.root
        val upload = File(root, "upload")
        File(upload, "sub").mkdirs()
        File(upload, "top.png").writeText("a")
        File(upload, "sub/nested.png").writeText("b")
        File(root, "skills").mkdirs()
        File(root, "skills/SKILL.md").writeText("c")

        val candidates = FilesCleanupPlanner.candidatesIn(root, FileFolders.UPLOAD)
        assertEquals(
            listOf("upload/sub/nested.png", "upload/top.png"),
            candidates.sorted(),
        )
    }

    @Test fun `a missing upload folder yields no candidates`() {
        assertTrue(FilesCleanupPlanner.candidatesIn(temporary.root, FileFolders.UPLOAD).isEmpty())
    }
}
