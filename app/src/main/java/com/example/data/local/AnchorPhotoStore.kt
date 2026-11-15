package com.example.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Keeps the user's pause-screen photo inside the app's private storage.
 *
 * A picked photo's content URI can stop working once the picker's grant expires, so the
 * image is copied. Each copy gets a new name so image caches never show the old photo.
 */
object AnchorPhotoStore {

    private const val TAG = "AnchorPhotoStore"
    private const val DIR = "anchor"

    /** Copies [source] in and returns the stored file's path, or null on failure. */
    fun save(context: Context, source: Uri): String? {
        return try {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val target = File(dir, "photo_" + System.currentTimeMillis() + ".jpg")
            val input = context.contentResolver.openInputStream(source) ?: return null
            input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
            dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Could not store photo", e)
            null
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /** The stored photo, if [path] still points at one. */
    fun fileFor(path: String): File? =
        path.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
}
