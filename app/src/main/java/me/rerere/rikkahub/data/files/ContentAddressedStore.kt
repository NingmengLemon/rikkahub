package me.rerere.rikkahub.data.files

import java.io.File
import java.io.InputStream

/**
 * 按内容寻址的文件落盘。
 *
 * 文件名是内容指纹，所以同一份内容只会有一份文件：写之前先看目标在不在，在就直接复用。
 * 引用仍然是指向这个文件的绝对路径，消费方不需要知道名字的来历。
 *
 * 只依赖 [File]，不碰 Android，测试用临时目录就能覆盖。
 */
internal object ContentAddressedStore {

    /** 写入字节内容，返回目标文件；已存在同名文件时直接返回它 */
    fun storeBytes(directory: File, bytes: ByteArray, extension: String): File {
        val target = File(directory, "${ContentHash.of(bytes)}.$extension")
        if (target.isFile) return target
        ensureDirectory(directory)
        // 先写同目录的临时文件再改名：中途失败不会留下一个被当成「已有的那份」的半截文件
        val temporary = File.createTempFile("incoming-", ".tmp", directory)
        try {
            temporary.writeBytes(bytes)
            if (target.isFile) return target
            check(temporary.renameTo(target)) { "Cannot store ${target.name}" }
            return target
        } finally {
            temporary.delete()
        }
    }

    /** 写入流的内容，边读边算指纹 */
    fun storeStream(directory: File, input: InputStream, extension: String): File {
        ensureDirectory(directory)
        val temporary = File.createTempFile("incoming-", ".tmp", directory)
        try {
            val digest = ContentHash.newDigest()
            temporary.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            val target = File(directory, "${ContentHash.hex(digest.digest())}.$extension")
            if (target.isFile) return target
            check(temporary.renameTo(target)) { "Cannot store ${target.name}" }
            return target
        } finally {
            temporary.delete()
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory) check(directory.mkdirs()) { "Cannot create ${directory.name}" }
    }
}
