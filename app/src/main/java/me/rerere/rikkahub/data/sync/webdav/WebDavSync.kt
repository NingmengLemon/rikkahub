package me.rerere.rikkahub.data.sync.webdav

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.BackupProgress
import me.rerere.rikkahub.data.sync.BackupTarget
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "WebDavSync"

class WebDavSync(
    private val backupManager: BackupManager,
    private val context: Context,
    private val httpClient: HttpClient,
) {
    private fun getClient(config: WebDavConfig): WebDavClient {
        return WebDavClient(config, httpClient)
    }

    suspend fun testConnection(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        // Test by listing the root directory
        client.propfind(depth = 0).getOrThrow()
        Log.i(TAG, "testConnection: Connection successful")
    }

    suspend fun backup(config: WebDavConfig) = withContext(Dispatchers.IO) {
        // WebDAV 出口目前只产出自家格式；官方兼容出口暂时只放在本地导出里
        val file = prepareBackupFile(config, BackupTarget.SELF)
        val client = getClient(config)

        // Ensure the backup directory exists
        client.ensureCollectionExists().getOrThrow()

        // Upload the backup file
        client.put(
            path = file.name,
            file = file,
            contentType = "application/zip"
        ).getOrThrow()

        Log.i(TAG, "backup: Uploaded ${file.name} (${file.length().fileSizeToString()})")

        // Clean up temp file
        file.delete()
    }

    suspend fun listBackupFiles(config: WebDavConfig): List<WebDavBackupItem> = withContext(Dispatchers.IO) {
        val client = getClient(config)

        // Ensure the backup directory exists
        client.ensureCollectionExists().getOrThrow()

        val resources = client.list().getOrThrow()

        resources
            .filter { !it.isCollection && it.displayName.startsWith("backup_") && it.displayName.endsWith(".zip") }
            .map { resource ->
                WebDavBackupItem(
                    href = resource.href,
                    displayName = resource.displayName,
                    size = resource.contentLength,
                    lastModified = resource.lastModified ?: Instant.EPOCH
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun restore(config: WebDavConfig, item: WebDavBackupItem) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        val backupFile = File.createTempFile("restore-", ".zip", context.cacheDir)

        try {
            // Download backup file directly to file to avoid OOM
            Log.i(TAG, "restore: Downloading ${item.displayName}")
            client.downloadToFile(item.displayName, backupFile).getOrThrow()

            Log.i(TAG, "restore: Downloaded ${backupFile.length().fileSizeToString()}")

            // Restore from backup file
            restoreFromBackupFile(backupFile, config)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restore: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteBackupFile(config: WebDavConfig, item: WebDavBackupItem) = withContext(Dispatchers.IO) {
        val client = getClient(config)
        client.delete(item.displayName).getOrThrow()
        Log.i(TAG, "deleteBackupFile: Deleted ${item.displayName}")
    }

    suspend fun restoreFromLocalFile(file: File, config: WebDavConfig) {
        restoreFromBackupFile(file, config)
    }

    suspend fun prepareBackupFile(
        config: WebDavConfig,
        target: BackupTarget,
        onProgress: ((BackupProgress) -> Unit)? = null,
    ): File = backupManager.createBackup(
        includeDatabase = WebDavConfig.BackupItem.DATABASE in config.items,
        includeFiles = WebDavConfig.BackupItem.FILES in config.items,
        backupTarget = target,
        onProgress = onProgress,
    )

    private suspend fun restoreFromBackupFile(backupFile: File, config: WebDavConfig) {
        // 本地导入与 WebDAV 导入都从这里进来，归档放在分流之前，两条路径都留了底。
        archiveImportedBackup(backupFile)
        backupManager.stageRestore(
            archive = backupFile,
            includeDatabase = WebDavConfig.BackupItem.DATABASE in config.items,
            includeFiles = WebDavConfig.BackupItem.FILES in config.items,
        )
    }

    /**
     * 导入过的原始备份留一份底，永不自动删除。
     *
     * 它是官方自己产出的自包含文件，随时能被官方吃回去，也是导入写坏数据时唯一能回退的东西。
     * 放 noBackupFilesDir：filesDir 整个目录被 web 路由暴露，不该放含全库的 zip。
     */
    private suspend fun archiveImportedBackup(source: File) = withContext(Dispatchers.IO) {
        val directory = File(context.noBackupFilesDir, "imported-backups")
        if (!directory.isDirectory && !directory.mkdirs()) {
            Log.w(TAG, "Cannot create imported backup directory")
            return@withContext
        }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        runCatching {
            source.copyTo(File(directory, "imported_$stamp.zip"), overwrite = true)
        }.onFailure { Log.w(TAG, "Cannot archive imported backup", it) }
    }
}

data class WebDavBackupItem(
    val href: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
