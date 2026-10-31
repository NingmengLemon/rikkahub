package me.rerere.rikkahub.data.files

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
/**
 * 文件内容的指纹。
 *
 * 用来给附件命名：同一份内容永远得到同一个名字，写之前先看目标在不在，在就复用，于是重复
 * 上传的同一张图只占一份空间。用 SHA-256 而不是更短的摘要，是因为这里的碰撞意味着两份不同
 * 的文件被当成同一份，代价太大。
 */
internal object ContentHash {

    private const val BUFFER_SIZE = 64 * 1024

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun of(file: File): String = file.inputStream().use(::of)

    /** 读完整个流后由调用方负责关闭 */
    fun of(input: InputStream): String {
        val digest = newDigest()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    fun newDigest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    /** 把摘要转成小写十六进制；与 [of] 的返回格式一致 */
    fun hex(digest: ByteArray): String {
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return builder.toString()
    }

    private const val HEX = "0123456789abcdef"
}
