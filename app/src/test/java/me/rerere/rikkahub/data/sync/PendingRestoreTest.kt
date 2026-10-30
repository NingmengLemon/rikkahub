package me.rerere.rikkahub.data.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PendingRestoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun restore() = PendingRestore(
        File(temporary.root, "restore"),
        database(),
        File(temporary.root, "files")
    )

    private fun database() = File(temporary.root, "databases/rikka_hub")

    private fun write(file: File, contents: String): File {
        file.parentFile!!.mkdirs()
        file.writeText(contents)
        return file
    }

    private fun stage(restore: PendingRestore): File = restore.createStagingDirectory().also {
        write(File(it, "payload/database/rikka_hub"), "new database")
        write(File(it, "payload/files/upload/photo"), "new photo")
        write(File(it, "settings.json"), "new settings")
        restore.publish(it)
    }

    @Test fun publishingDoesNotTouchLiveFiles() {
        write(database(), "old database")
        write(File(database().path + "-wal"), "old wal")
        stage(restore())
        assertEquals("old database", database().readText())
        assertEquals("old wal", File(database().path + "-wal").readText())
        assertFalse(File(temporary.root, "files/upload/photo").exists())
    }

    @Test fun installsStandaloneDatabaseAndDoesNotReplayOnLaterLaunches() = runBlocking {
        write(database(), "old database")
        listOf("-wal", "-shm", "-journal").forEach { write(File(database().path + it), "old") }
        stage(restore())
        val restored = restore().apply {
            assertEquals("new settings", it)
            assertEquals("new database", database().readText())
            assertEquals("new photo", File(temporary.root, "files/upload/photo").readText())
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                assertFalse(File(database().path + suffix).exists())
            }
        }
        assertTrue(restored)
        database().writeText("subsequent user changes")
        assertFalse(restore().apply { error("Settings must not be applied again") })
        assertEquals("subsequent user changes", database().readText())
    }

    @Test fun replacingAttachmentFolderRemovesFilesMissingFromBackup() = runBlocking {
        write(File(temporary.root, "files/upload/stale"), "stale upload")
        write(File(temporary.root, "files/skills/old/SKILL.md"), "old skill")
        stage(restore())
        restore().apply { }
        assertEquals("new photo", File(temporary.root, "files/upload/photo").readText())
        assertFalse(File(temporary.root, "files/upload/stale").exists())
        // skills 不在备份里，说明归档没带这个目录，不参与替换
        assertTrue(File(temporary.root, "files/skills/old/SKILL.md").exists())
    }

    @Test fun failedRestoreRestoresAttachmentsRemovedByReplacement() = runBlocking {
        write(File(temporary.root, "files/upload/stale"), "stale upload")
        stage(restore())
        try {
            restore().apply {
                // 进入 settings 回调时删除条目已经生效过，先确认它真的被移走了，
                // 否则这条测试在「没有删除逻辑」的旧实现上也会通过。
                assertFalse(File(temporary.root, "files/upload/stale").exists())
                throw IOException("Disk full")
            }
            error("Expected failure")
        } catch (_: RestoreFailedException) {
            assertEquals("stale upload", File(temporary.root, "files/upload/stale").readText())
        }
    }

    @Test fun databaseOnlyRestoreKeepsLiveAttachments() = runBlocking {
        write(File(temporary.root, "files/upload/stale"), "stale upload")
        write(database(), "old database")
        val subject = restore()
        val staging = subject.createStagingDirectory()
        write(File(staging, "payload/database/rikka_hub"), "new database")
        write(File(staging, "settings.json"), "new settings")
        subject.publish(staging)
        restore().apply { }
        assertEquals("stale upload", File(temporary.root, "files/upload/stale").readText())
    }

    @Test fun filesOnlyRestoreKeepsAttachmentsMissingFromBackup() = runBlocking {
        write(database(), "old database")
        write(File(temporary.root, "files/upload/stale"), "stale upload")
        val subject = restore()
        val staging = subject.createStagingDirectory()
        write(File(staging, "payload/files/upload/photo"), "new photo")
        write(File(staging, "settings.json"), "new settings")
        subject.publish(staging)
        restore().apply { }
        // 库没换，现有库还引用着 live 上的附件，不能删
        assertEquals("stale upload", File(temporary.root, "files/upload/stale").readText())
        assertEquals("new photo", File(temporary.root, "files/upload/photo").readText())
    }

    @Test fun nestedAttachmentFolderIsReplacedRecursively() = runBlocking {
        write(File(temporary.root, "files/skills/old/SKILL.md"), "old skill")
        write(database(), "old database")
        val subject = restore()
        val staging = subject.createStagingDirectory()
        write(File(staging, "payload/database/rikka_hub"), "new database")
        write(File(staging, "payload/files/skills/new/SKILL.md"), "new skill")
        write(File(staging, "settings.json"), "new settings")
        subject.publish(staging)
        restore().apply { }
        assertFalse(File(temporary.root, "files/skills/old/SKILL.md").exists())
        assertEquals("new skill", File(temporary.root, "files/skills/new/SKILL.md").readText())
    }

    @Test fun failedSettingsWriteRestoresOriginalDatabaseAndSidecarsAndCanRetry() = runBlocking {
        write(database(), "old database")
        write(File(database().path + "-wal"), "old wal")
        write(File(temporary.root, "files/upload/photo"), "old photo")
        stage(restore())
        try {
            restore().apply { throw IOException("Disk full") }
            error("Expected failure")
        } catch (_: RestoreFailedException) {
            assertEquals("old database", database().readText())
            assertEquals("old wal", File(database().path + "-wal").readText())
            assertEquals("old photo", File(temporary.root, "files/upload/photo").readText())
        }
        stage(restore())
        restore().apply { }
        assertEquals("new database", database().readText())
        assertFalse(File(database().path + "-wal").exists())
    }

    @Test fun failedInitialJournalWriteKeepsOriginalDataAndDoesNotBlockLaterLaunches() = runBlocking {
        write(database(), "old database")
        write(File(database().path + "-wal"), "old wal")
        write(File(temporary.root, "files/upload/photo"), "old photo")
        stage(restore())
        // Force a real I/O failure without relying on disk capacity or filesystem permissions.
        assertTrue(File(temporary.root, "restore/pending/journal.json.tmp").mkdir())

        try {
            restore().apply { error("Settings must not be applied without a journal") }
            error("Expected failure")
        } catch (e: RestoreFailedException) {
            assertTrue(e.cause is IOException)
        }

        assertEquals("old database", database().readText())
        assertEquals("old wal", File(database().path + "-wal").readText())
        assertEquals("old photo", File(temporary.root, "files/upload/photo").readText())
        assertFalse(File(temporary.root, "restore/pending").exists())
        val failed = File(temporary.root, "restore").listFiles()!!.single { it.name.startsWith("failed-") }
        assertEquals("new database", File(failed, "payload/database/rikka_hub").readText())
        assertFalse(restore().apply { error("Failed restore must not be replayed") })

        stage(restore())
        assertTrue(restore().apply { })
        assertEquals("new database", database().readText())
    }

    @Test fun invalidJournalAfterInterruptionKeepsOriginalsForRecovery() = runBlocking {
        write(database(), "old database")
        stage(restore())
        try {
            restore().apply { throw SimulatedProcessDeath() }
        } catch (_: SimulatedProcessDeath) {
            // The live database has been replaced, but the original is still needed for recovery.
        }
        val pending = File(temporary.root, "restore/pending")
        File(pending, "journal.json").writeText("invalid journal")

        try {
            restore().apply { error("Settings must not be applied with an invalid journal") }
            error("Expected failure")
        } catch (_: SerializationException) {
            // Startup must stop because the live files may already have been replaced.
        }

        assertTrue(pending.isDirectory)
        assertEquals("old database", File(pending, "originals/database/rikka_hub").readText())
        assertEquals("new database", database().readText())
    }

    @Test fun restartResumesInstallationInterruptedBeforeCommit() = runBlocking {
        write(database(), "old database")
        write(File(database().path + "-wal"), "old wal")
        stage(restore())
        try {
            // Error bypasses ordinary exception rollback, simulating process loss after file moves.
            restore().apply { throw SimulatedProcessDeath() }
        } catch (_: SimulatedProcessDeath) {
            assertTrue(File(temporary.root, "restore/pending").exists())
        }
        restore().apply { assertEquals("new settings", it) }
        assertEquals("new database", database().readText())
        assertFalse(File(database().path + "-wal").exists())
        assertFalse(File(temporary.root, "restore/pending").exists())
    }

    @Test fun filesOnlyRestoreLeavesDatabaseAndWalUntouched() = runBlocking {
        write(database(), "old database")
        write(File(database().path + "-wal"), "old wal")
        val subject = restore()
        val staging = subject.createStagingDirectory()
        write(File(staging, "payload/files/fonts/custom.ttf"), "font")
        subject.publish(staging)
        subject.apply { error("No settings in backup") }
        assertEquals("old database", database().readText())
        assertEquals("old wal", File(database().path + "-wal").readText())
        assertEquals("font", File(temporary.root, "files/fonts/custom.ttf").readText())
    }

    @Test fun cannotReplaceAlreadyPendingRestore() {
        stage(restore())
        assertThrows(IllegalStateException::class.java) { restore().createStagingDirectory() }
    }

    @Test fun rejectsArchiveTraversalPaths() {
        for (path in listOf("../database", "/absolute", "skills/../../database", "skills/..\\database")) {
            assertThrows(IllegalArgumentException::class.java) {
                PendingRestore.resolveInside(temporary.root, path)
            }
        }
    }

    private class SimulatedProcessDeath : Error()
}
