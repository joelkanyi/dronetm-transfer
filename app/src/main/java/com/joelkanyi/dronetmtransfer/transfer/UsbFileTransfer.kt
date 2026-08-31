package com.joelkanyi.dronetmtransfer.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLConnection

class UsbFileTransfer(private val context: Context) {

    suspend fun list(treeUri: Uri): DirectoryListing = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        DirectoryListing(
            rootName = root?.name ?: "?",
            entries = root?.listFiles().orEmpty().map { child ->
                Entry(
                    name = child.name ?: "?",
                    isDirectory = child.isDirectory,
                    sizeBytes = child.length(),
                )
            },
        )
    }

    suspend fun copyTree(
        sourceTreeUri: Uri,
        destTreeUri: Uri,
        onProgress: (fileName: String, done: Int, total: Int) -> Unit,
    ): CopyOutcome = withContext(Dispatchers.IO) {
        val sourceRoot = DocumentFile.fromTreeUri(context, sourceTreeUri)
        if (sourceRoot == null || !sourceRoot.canRead()) {
            return@withContext CopyOutcome.SourceUnavailable
        }
        val destRoot = DocumentFile.fromTreeUri(context, destTreeUri)
        if (destRoot == null || !destRoot.canWrite()) {
            return@withContext CopyOutcome.DestinationUnavailable
        }
        val planned = collect(sourceRoot, segments = emptyList())
        val results = planned.mapIndexed { index, item ->
            onProgress(item.name, index, planned.size)
            copyOne(item, destRoot)
        }
        CopyOutcome.Completed(TransferReport(results))
    }

    private fun collect(dir: DocumentFile, segments: List<String>): List<PlannedCopy> =
        dir.listFiles().flatMap { child ->
            val name = child.name ?: return@flatMap emptyList()
            if (child.isDirectory) {
                collect(child, segments + name)
            } else {
                listOf(PlannedCopy(child.uri, segments, name))
            }
        }

    private fun copyOne(item: PlannedCopy, destRoot: DocumentFile): TransferResult =
        try {
            val targetDir = ensureDir(destRoot, item.segments)
            val mimeType = URLConnection.guessContentTypeFromName(item.name)
                ?: "application/octet-stream"
            val target = targetDir.createFile(mimeType, item.name)
                ?: return TransferResult.Failed(item.name, "could not create in destination")
            val bytes = context.contentResolver.openInputStream(item.sourceUri)?.use { source ->
                context.contentResolver.openOutputStream(target.uri)?.use { sink ->
                    source.copyTo(sink)
                }
            }
            if (bytes == null) {
                TransferResult.Failed(item.name, "could not open stream")
            } else {
                TransferResult.Copied(item.name, bytes)
            }
        } catch (e: IOException) {
            TransferResult.Failed(item.name, e.message ?: "I/O error")
        } catch (e: SecurityException) {
            TransferResult.Failed(item.name, "permission lost")
        }

    private fun ensureDir(root: DocumentFile, segments: List<String>): DocumentFile =
        segments.fold(root) { dir, name ->
            dir.findFile(name)?.takeIf { it.isDirectory } ?: dir.createDirectory(name) ?: dir
        }

    private data class PlannedCopy(
        val sourceUri: Uri,
        val segments: List<String>,
        val name: String,
    )
}
