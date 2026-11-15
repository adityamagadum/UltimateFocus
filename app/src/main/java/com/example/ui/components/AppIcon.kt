package com.example.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusSurfaceContainerHighest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real launcher icon of an installed app, loaded off the main thread.
 *
 * Falls back to a neutral glyph when the app is not installed or cannot be read.
 */
@Composable
fun AppIcon(
    packageName: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val px = (size.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val cacheKey = "$packageName@$px"
    // Starts from the cache, so an icon seen once shows instantly on every later visit
    // instead of flashing the placeholder each time the screen opens.
    val icon by produceState(initialValue = IconCache.get(cacheKey), cacheKey) {
        val cached = IconCache.get(cacheKey)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = null // never keep showing a different app's icon while this one loads
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = px, height = px)
                    .asImageBitmap()
                    .also { IconCache.put(cacheKey, it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(FocusSurfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = FocusOnSurfaceVariant,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

/** Small in-memory store of rendered app icons, shared by every screen. */
private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(64)
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}
