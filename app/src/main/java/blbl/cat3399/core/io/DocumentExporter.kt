package blbl.cat3399.core.io

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object DocumentExporter {
    data class ExportResult<T>(
        val fileName: String,
        val uri: Uri,
        val value: T,
    )

    data class LocalExportResult<T>(
        val fileName: String,
        val file: File,
        val value: T,
    )

    fun <T> exportToTreeUri(
        context: Context,
        treeUri: Uri,
        mimeType: String,
        fileName: String,
        writeTo: (OutputStream) -> T,
    ): ExportResult<T> {
        val appContext = context.applicationContext
        val safeFileName = sanitizeFileName(fileName)
        if (safeFileName.isBlank()) throw IOException("导出文件名无效")

        val outUri = createDocument(appContext, treeUri = treeUri, mimeType = mimeType, baseName = safeFileName)
        val value =
            appContext.contentResolver.openOutputStream(outUri, "w")?.use { rawOut ->
                BufferedOutputStream(rawOut, 32 * 1024).use { bufferedOut ->
                    writeTo(bufferedOut).also { bufferedOut.flush() }
                }
            } ?: throw IOException("无法写入导出文件")

        return ExportResult(fileName = safeFileName, uri = outUri, value = value)
    }

    fun <T> exportToLocalFile(
        context: Context,
        fileName: String,
        subDir: String = "exports",
        writeTo: (OutputStream) -> T,
    ): LocalExportResult<T> {
        val appContext = context.applicationContext
        val safeFileName = sanitizeFileName(fileName)
        if (safeFileName.isBlank()) throw IOException("导出文件名无效")

        val exportDir =
            appContext.getExternalFilesDir(subDir)
                ?: File(appContext.filesDir, subDir)
        runCatching { exportDir.mkdirs() }

        val outFile = createLocalFile(dir = exportDir, baseName = safeFileName)
        val value =
            FileOutputStream(outFile).use { rawOut ->
                BufferedOutputStream(rawOut, 32 * 1024).use { bufferedOut ->
                    writeTo(bufferedOut).also { bufferedOut.flush() }
                }
            }

        return LocalExportResult(fileName = outFile.name, file = outFile, value = value)
    }

    private fun createDocument(
        context: Context,
        treeUri: Uri,
        mimeType: String,
        baseName: String,
    ): Uri {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

        var attempt = 0
        while (attempt <= 20) {
            val name = appendAttemptSuffix(baseName = baseName, attempt = attempt)
            try {
                val created = DocumentsContract.createDocument(resolver, dirUri, mimeType, name)
                if (created != null) return created
            } catch (_: Throwable) {
                // Try a different file name when the provider rejects duplicates.
            }
            attempt++
        }
        throw IOException("创建导出文件失败")
    }

    private fun createLocalFile(
        dir: File,
        baseName: String,
    ): File {
        var attempt = 0
        while (attempt <= 20) {
            val name = appendAttemptSuffix(baseName = baseName, attempt = attempt)
            val file = File(dir, name)
            try {
                if (file.createNewFile()) return file
            } catch (_: Throwable) {
                // Try a different file name.
            }
            attempt++
        }
        throw IOException("创建导出文件失败")
    }

    private fun appendAttemptSuffix(
        baseName: String,
        attempt: Int,
    ): String {
        if (attempt == 0) return baseName
        val dotIndex = baseName.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == baseName.lastIndex) {
            return "${baseName}_$attempt"
        }
        val prefix = baseName.substring(0, dotIndex)
        val suffix = baseName.substring(dotIndex)
        return "${prefix}_$attempt$suffix"
    }

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ""
        val noSeparators = trimmed.replace(Regex("[\\\\/\\r\\n\\t]"), "_")
        return noSeparators.take(96)
    }
}
