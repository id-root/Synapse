package com.synapse.lantransfer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.synapse.lantransfer.data.model.SelectedFile

/**
 * Handles file selection and metadata extraction using the Storage Access Framework.
 * Converts content URIs to SelectedFile objects with name, size, and MIME type.
 */
class FileRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Convert a list of content URIs (from the file picker) to SelectedFile objects.
     */
    fun resolveFiles(uris: List<Uri>): List<SelectedFile> {
        return uris.mapNotNull { uri -> resolveFile(uri) }
    }

    /**
     * Resolve a single content URI to a SelectedFile.
     */
    fun resolveFile(uri: Uri): SelectedFile? {
        return try {
            val name = getDisplayName(uri) ?: uri.lastPathSegment ?: "unknown"
            val size = getFileSize(uri)
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

            SelectedFile(
                name = name,
                size = size,
                uri = uri.toString(),
                mimeType = mimeType
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        }
        return 0L
    }
}
