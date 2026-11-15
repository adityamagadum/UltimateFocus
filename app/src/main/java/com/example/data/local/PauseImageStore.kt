package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Stores the pause screen's background photo.
 *
 * A gallery photo can be 5–15 MB. Loading one that size every time a blocked app opens
 * would make the pause screen slow and could run the phone out of memory, so the photo
 * is shrunk to screen size and saved once as a compact JPEG (typically 200–400 KB).
 */
object PauseImageStore {

    private const val TAG = "PauseImageStore"
    private const val DIR = "pause"

    /** Longest side kept. Sharp on any phone screen without wasting memory. */
    private const val MAX_SIDE_PX = 1600
    private const val JPEG_QUALITY = 85

    /** Imports [source] and returns the saved file's path, or null on failure. Call off the main thread. */
    fun importBackground(context: Context, source: Uri): String? = try {
        val bitmap = decodeScaled(context, source) ?: return null
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        // A new name each time, so image caches never show the previous photo.
        val target = File(dir, "bg_" + System.currentTimeMillis() + ".jpg")
        target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        bitmap.recycle()
        target.absolutePath
    } catch (e: Exception) {
        Log.e(TAG, "Could not import background", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.e(TAG, "Photo too large to import", e)
        null
    }

    /**
     * Deletes stored photos and videos other than [keep], so old ones don't pile up.
     * Videos live in the same folder (see PauseVideoStore).
     */
    fun cleanup(context: Context, keep: Set<String>) {
        File(context.filesDir, DIR).listFiles()
            ?.filter { it.absolutePath !in keep }
            ?.forEach { it.delete() }
    }

    fun fileFor(path: String): File? =
        path.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }

    private fun decodeScaled(context: Context, source: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also applies the photo's rotation (EXIF) for us.
            val src = ImageDecoder.createSource(resolver, source)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val ratio = max(w, h).toFloat() / MAX_SIDE_PX
                if (ratio > 1f) {
                    decoder.setTargetSize((w / ratio).roundToInt(), (h / ratio).roundToInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        // Android 7–8: read the size first, decode at a reduced size, then rotate.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val ratio = max(bitmap.width, bitmap.height).toFloat() / MAX_SIDE_PX
        if (ratio > 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width / ratio).roundToInt(), (bitmap.height / ratio).roundToInt(), true
            )
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val degrees = resolver.openInputStream(source)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return bitmap
    }
}
