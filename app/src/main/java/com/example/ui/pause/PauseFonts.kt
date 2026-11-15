package com.example.ui.pause

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.concurrent.ConcurrentHashMap

/**
 * One font the pause-screen editor offers.
 *
 * Fonts are free Google Fonts (SIL Open Font License) loaded from
 * `app/src/main/assets/fonts/`. If a file is missing the [fallback] system font is used,
 * so the app always works.
 */
data class PauseFont(
    val id: String,
    val name: String,
    val assetFile: String,
    val fallback: FontFamily,
    val fallbackWeight: FontWeight,
    /** One line on when to use it, shown in the font picker. */
    val mood: String
)

object PauseFonts {

    private const val TAG = "PauseFonts"

    val all: List<PauseFont> = listOf(
        PauseFont("bebas", "Bebas Neue", "BebasNeue-Regular.ttf", FontFamily.SansSerif, FontWeight.Black, "Bold, short commands"),
        PauseFont("anton", "Anton", "Anton-Regular.ttf", FontFamily.SansSerif, FontWeight.ExtraBold, "Loud headlines"),
        PauseFont("playfair", "Playfair", "PlayfairDisplay-Bold.ttf", FontFamily.Serif, FontWeight.Bold, "Elegant quotes"),
        PauseFont("lora", "Lora", "Lora-Medium.ttf", FontFamily.Serif, FontWeight.Medium, "Calm, longer lines"),
        PauseFont("montserrat", "Montserrat", "Montserrat-SemiBold.ttf", FontFamily.SansSerif, FontWeight.SemiBold, "Clean and clear"),
        PauseFont("poppins", "Poppins", "Poppins-SemiBold.ttf", FontFamily.SansSerif, FontWeight.Medium, "Friendly, modern"),
        PauseFont("caveat", "Caveat", "Caveat-Bold.ttf", FontFamily.Cursive, FontWeight.Bold, "Handwritten note"),
        PauseFont("greatvibes", "Great Vibes", "GreatVibes-Regular.ttf", FontFamily.Cursive, FontWeight.Normal, "Script accent")
    )

    private val byId = all.associateBy { it.id }
    private val loaded = ConcurrentHashMap<String, FontFamily>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun font(id: String): PauseFont = byId[id] ?: byId.getValue("montserrat")

    /** Weight to draw with: the file's own weight, or a matching weight for the fallback. */
    fun weightFor(id: String): FontWeight =
        if (loaded.containsKey(id)) FontWeight.Normal else font(id).fallbackWeight

    /**
     * The font family for [id]. Fast after the first call; [preload] warms every font at
     * app start so the pause screen never waits on disk.
     */
    fun family(context: Context, id: String): FontFamily {
        loaded[id]?.let { return it }
        val font = font(id)
        if (id in missing) return font.fallback
        return try {
            val typeface = Typeface.createFromAsset(context.assets, "fonts/" + font.assetFile)
            FontFamily(typeface).also { loaded[id] = it }
        } catch (e: Exception) {
            Log.i(TAG, "fonts/${font.assetFile} not bundled, using the system font")
            missing.add(id)
            font.fallback
        }
    }

    /** Loads every font once, off the main thread. */
    fun preload(context: Context) {
        all.forEach { family(context.applicationContext, it.id) }
    }
}
