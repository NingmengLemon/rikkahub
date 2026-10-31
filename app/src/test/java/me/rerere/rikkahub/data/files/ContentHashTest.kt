package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

/** 内容指纹本身的验证：已知向量、编码格式、流与字节一致 */
class ContentHashTest {

    @Test fun `matches the known sha256 of empty content`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ContentHash.of(ByteArray(0)),
        )
    }

    @Test fun `matches the known sha256 of abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ContentHash.of("abc".toByteArray()),
        )
    }

    @Test fun `stream produces the same digest as bytes`() {
        val bytes = "some attachment body".toByteArray()
        assertEquals(ContentHash.of(bytes), java.io.ByteArrayInputStream(bytes).let(ContentHash::of))
    }

    @Test fun `digest is lowercase hex of fixed length`() {
        val hash = ContentHash.of("x".toByteArray())
        assertEquals(64, hash.length)
        assertEquals(hash.lowercase(), hash)
        assertEquals(true, hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun `the same bytes always give the same digest`() {
        val bytes = ByteArray(1024) { (it % 251).toByte() }
        assertEquals(ContentHash.of(bytes), ContentHash.of(bytes.copyOf()))
    }

    @Test fun `a single flipped byte changes the digest`() {
        val bytes = ByteArray(64) { it.toByte() }
        val flipped = bytes.copyOf().also { it[10] = (it[10] + 1).toByte() }
        assertEquals(false, ContentHash.of(bytes) == ContentHash.of(flipped))
    }
}
