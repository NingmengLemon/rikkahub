package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportedBackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun storesArchiveAndKeepsContents() {
        val source = File(temporary.root, "backup.zip").apply { writeText("official backup") }
        val directory = File(temporary.root, "imported-backups")
        val archived = ImportedBackupArchive.store(source, directory)
        assertNotNull(archived)
        assertTrue(archived!!.isFile)
        assertEquals("official backup", archived.readText())
    }

    @Test fun keepsPreviousArchivesInsteadOfOverwriting() {
        val source = File(temporary.root, "backup.zip").apply { writeText("official backup") }
        val directory = File(temporary.root, "imported-backups")
        val first = ImportedBackupArchive.store(source, directory)
        val second = ImportedBackupArchive.store(source, directory)
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
        assertEquals(2, directory.listFiles()!!.count { it.isFile && it.name.endsWith(".zip") })
    }

    @Test fun leavesNoTemporaryFileBehind() {
        val source = File(temporary.root, "backup.zip").apply { writeText("official backup") }
        val directory = File(temporary.root, "imported-backups")
        ImportedBackupArchive.store(source, directory)
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test fun returnsNullWhenTheSourceIsMissing() {
        val directory = File(temporary.root, "imported-backups")
        assertNull(ImportedBackupArchive.store(File(temporary.root, "absent.zip"), directory))
    }
}
