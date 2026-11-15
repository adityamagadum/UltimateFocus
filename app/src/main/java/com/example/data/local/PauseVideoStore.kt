package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Imports a gallery video as the pause screen's background.
 *
 * Phone videos are often 4K with sound, 50–200 MB a minute. Playing one every time a
 * blocked app opens would be slow and heavy, so the video is compressed once on import:
 * the first 15 seconds, short side 720 px, H.264 at 1.5 Mbps, sound removed. That is
 * about 2–3 MB, starts instantly, and loops smoothly. Like photos, it is stored in the
 * app's private folder, so the original in the gallery is never touched.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object PauseVideoStore {

    private const val TAG = "PauseVideoStore"
    private const val DIR = "pause"

    /** Longest clip kept, in milliseconds. A pause screen is seen for seconds, not minutes. */
    const val MAX_DURATION_MS = 15_000L
    private const val SHORT_SIDE_PX = 720
    private const val VIDEO_BITRATE = 1_500_000
    private const val POSTER_MAX_SIDE_PX = 1280

    data class ImportedVideo(
        val videoPath: String,
        val posterPath: String,
        val width: Int,
        val height: Int
    )

    private data class VideoInfo(val width: Int, val height: Int)

    /**
     * Compresses [source] into the app's storage, reporting progress 0–100.
     * Returns null if the video can't be read or compressed. Cancelling the calling
     * coroutine stops the compression.
     */
    suspend fun import(context: Context, source: Uri, onProgress: (Int) -> Unit): ImportedVideo? {
        val app = context.applicationContext
        val info = withContext(Dispatchers.IO) { readInfo(app, source) } ?: return null
        val dir = File(app.filesDir, DIR).apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val videoFile = File(dir, "vid_$stamp.mp4")
        val posterFile = File(dir, "vid_${stamp}_poster.jpg")

        val ok = try {
            withContext(Dispatchers.Main) { transcode(app, source, info, videoFile, onProgress) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                videoFile.delete()
                throw e
            }
            Log.e(TAG, "Compression failed", e)
            false
        }
        if (!ok || !videoFile.exists() || videoFile.length() == 0L) {
            videoFile.delete()
            return null
        }

        val output = withContext(Dispatchers.IO) {
            writePoster(app, videoFile, posterFile)
            readInfo(app, Uri.fromFile(videoFile))
        } ?: info

        return ImportedVideo(
            videoPath = videoFile.absolutePath,
            posterPath = if (posterFile.exists()) posterFile.absolutePath else "",
            width = output.width,
            height = output.height
        )
    }

    fun fileFor(path: String): File? =
        path.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }

    /** Width and height as the video is shown (rotation applied). */
    private fun readInfo(context: Context, uri: Uri): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0) null
            else if (rotation == 90 || rotation == 270) VideoInfo(h, w) else VideoInfo(w, h)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read video", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    /** Saves the first frame as a small JPEG, shown while the video starts playing. */
    private fun writePoster(context: Context, video: File, target: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.fromFile(video))
            var frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return
            val ratio = max(frame.width, frame.height).toFloat() / POSTER_MAX_SIDE_PX
            if (ratio > 1f) {
                val scaled = Bitmap.createScaledBitmap(
                    frame, (frame.width / ratio).roundToInt(), (frame.height / ratio).roundToInt(), true
                )
                if (scaled != frame) frame.recycle()
                frame = scaled
            }
            target.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            frame.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save poster frame", e)
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    /** Runs Media3 Transformer. Must be called on the main thread. */
    private suspend fun transcode(
        context: Context,
        source: Uri,
        info: VideoInfo,
        output: File,
        onProgress: (Int) -> Unit
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val effects = if (min(info.width, info.height) > SHORT_SIDE_PX) {
            // Shrinks so the short side is 720 px, keeping the shape. Presentation sizes
            // by output height: a portrait video (taller than wide) needs a height that
            // leaves its width at 720; a landscape one simply gets a height of 720.
            val targetHeight = if (info.height > info.width) {
                (SHORT_SIDE_PX.toLong() * info.height / info.width).toInt()
            } else {
                SHORT_SIDE_PX
            }
            Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight and 1.inv())))
        } else {
            Effects.EMPTY
        }

        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(MAX_DURATION_MS)
                    .build()
            )
            .build()

        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(effects)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build()
            )
            .build()

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onProgress(100)
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    Log.e(TAG, "Video export failed", exportException)
                    if (continuation.isActive) continuation.resume(false)
                }
            })
            .build()

        val poll = object : Runnable {
            override fun run() {
                if (!continuation.isActive) return
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                }
                handler.postDelayed(this, 200)
            }
        }

        transformer.start(edited, output.absolutePath)
        handler.post(poll)

        continuation.invokeOnCancellation {
            handler.post {
                handler.removeCallbacks(poll)
                transformer.cancel()
            }
        }
    }
}
