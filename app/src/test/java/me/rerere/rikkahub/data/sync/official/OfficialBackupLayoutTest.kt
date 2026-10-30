package me.rerere.rikkahub.data.sync.official

import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.sync.DatabaseBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 归档布局快照与仓库里在用的常量对账。
 *
 * 快照刻意独立于 [FileFolders] 与 [DatabaseBackup]：它们是本仓库的实现细节，会随
 * 我们自己的改动变化；快照描述的是官方那侧，两者分开才能发现「我们改了但官方没改」。
 */
class OfficialBackupLayoutTest {

    @Test
    fun `attachment folders are exactly the ones the backup writes today`() {
        assertEquals(
            setOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS, FileFolders.MEDIA_CREATION),
            OfficialBackupLayout.attachmentFolders,
        )
    }

    @Test
    fun `nested folders are a subset of the attachment folders`() {
        assertTrue(OfficialBackupLayout.nestedAttachmentFolders.all { it in OfficialBackupLayout.attachmentFolders })
        assertEquals(
            setOf(FileFolders.SKILLS, FileFolders.MEDIA_CREATION),
            OfficialBackupLayout.nestedAttachmentFolders
        )
    }

    @Test
    fun `database entry matches the archive name used by the backup`() {
        assertEquals(DatabaseBackup.ARCHIVE_DATABASE, OfficialBackupLayout.DATABASE_ENTRY)
    }

    @Test
    fun `attachment entries are recognised by their leading folder`() {
        assertTrue(OfficialBackupLayout.isAttachmentEntry("upload/a.png"))
        assertTrue(OfficialBackupLayout.isAttachmentEntry("skills/demo/SKILL.md"))
        assertFalse(OfficialBackupLayout.isAttachmentEntry("settings.json"))
        assertFalse(OfficialBackupLayout.isAttachmentEntry("rikka_hub.db"))
        assertFalse(OfficialBackupLayout.isAttachmentEntry("tool_outputs/x.txt"))
    }
}
