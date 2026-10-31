package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 内容寻址落盘的验证。
 *
 * 真实样本上的规模是 8080 个附件只有 679 份不同内容，所以「同一份只落一次」是这批的全部
 * 意义；这里逐条钉住它，包括「写同样的内容两次不会多出文件」。
 */
class ContentAddressedStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun directory(): File = File(temporary.root, "upload")

    @Test fun `the same content lands on the same file`() {
        val first = ContentAddressedStore.storeBytes(directory(), "hello".toByteArray(), "txt")
        val second = ContentAddressedStore.storeBytes(directory(), "hello".toByteArray(), "txt")
        assertEquals(first, second)
        assertEquals(1, directory().listFiles()!!.size)
    }

    @Test fun `different content lands on different files`() {
        val first = ContentAddressedStore.storeBytes(directory(), "one".toByteArray(), "txt")
        val second = ContentAddressedStore.storeBytes(directory(), "two".toByteArray(), "txt")
        assertNotEquals(first, second)
        assertEquals(2, directory().listFiles()!!.size)
    }

    @Test fun `the file name is the content digest`() {
        val bytes = "hello".toByteArray()
        val file = ContentAddressedStore.storeBytes(directory(), bytes, "txt")
        assertEquals("${ContentHash.of(bytes)}.txt", file.name)
    }

    @Test fun `storing the same content twice keeps the original bytes`() {
        val first = ContentAddressedStore.storeBytes(directory(), "hello".toByteArray(), "txt")
        val second = ContentAddressedStore.storeBytes(directory(), "hello".toByteArray(), "txt")
        assertEquals("hello", second.readText())
        assertEquals(first.lastModified(), second.lastModified())
    }

    @Test fun `streaming and bytes agree on the same file`() {
        val bytes = "streamed".toByteArray()
        val fromBytes = ContentAddressedStore.storeBytes(directory(), bytes, "bin")
        val fromStream = ContentAddressedStore.storeStream(directory(), ByteArrayInputStream(bytes), "bin")
        assertEquals(fromBytes, fromStream)
        assertEquals(1, directory().listFiles()!!.size)
    }

    @Test fun `no temporary file is left behind`() {
        ContentAddressedStore.storeBytes(directory(), "hello".toByteArray(), "txt")
        ContentAddressedStore.storeStream(directory(), ByteArrayInputStream("world".toByteArray()), "txt")
        assertTrue(directory().listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test fun `a missing directory is created`() {
        val nested = File(File(temporary.root, "a"), "b")
        val file = ContentAddressedStore.storeBytes(nested, "x".toByteArray(), "txt")
        assertTrue(file.isFile)
    }

    @Test fun `duplicate extension keeps a stable name`() {
        // 同一内容用不同扩展名会得到两个文件；这是有意的，因为扩展名影响系统如何打开它
        val bytes = "same".toByteArray()
        val png = ContentAddressedStore.storeBytes(directory(), bytes, "png")
        val jpg = ContentAddressedStore.storeBytes(directory(), bytes, "jpg")
        assertNotEquals(png, jpg)
    }
}
