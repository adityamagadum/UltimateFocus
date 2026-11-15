package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * The user's own pause screen: a full-screen background with text placed anywhere on it.
 *
 * Positions and sizes are stored as fractions of the screen, not pixels, so a design
 * looks the same on every phone and in the editor's smaller preview.
 */
@JsonClass(generateAdapter = true)
data class PauseDesign(
    val background: PauseBackground = PauseBackground(),
    val layers: List<TextLayer> = emptyList(),
    /** The small card at the top saying what was blocked. */
    val showBlockInfo: Boolean = true
) {
    companion object {
        /** Builds a design from the older message-and-photo settings, so nothing is lost. */
        fun fromLegacy(config: InterventionConfig): PauseDesign {
            val layers = buildList {
                if (config.customQuote.isNotBlank()) {
                    add(
                        TextLayer(
                            id = "legacy_quote",
                            text = config.customQuote,
                            fontId = "playfair",
                            sizeFraction = 0.075f,
                            y = 0.42f
                        )
                    )
                }
                if (config.subMessage.isNotBlank()) {
                    add(
                        TextLayer(
                            id = "legacy_sub",
                            text = config.subMessage,
                            fontId = "montserrat",
                            sizeFraction = 0.042f,
                            y = 0.55f
                        )
                    )
                }
                add(
                    TextLayer(
                        id = "legacy_goal",
                        text = "Your goal: {goal}",
                        fontId = "montserrat",
                        sizeFraction = 0.038f,
                        colorArgb = 0xFF4EDEA3,
                        y = 0.64f,
                        style = TextLayerStyle.NONE
                    )
                )
            }
            return PauseDesign(
                background = PauseBackground(
                    presetId = "forest",
                    photoPath = config.anchorImageUri,
                    dim = if (config.anchorImageUri.isNotBlank()) 0.45f else 0.15f
                ),
                layers = layers
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class PauseBackground(
    /** A built-in background, used whenever there is no photo. */
    val presetId: String = "forest",
    /** A photo from the gallery, already shrunk to screen size. Empty means none. */
    val photoPath: String = "",
    /**
     * A short looping video from the gallery, already compressed (720p, no sound,
     * at most 15 s). Shown instead of the photo or preset when set.
     */
    val videoPath: String = "",
    /** The video's first frame, shown instantly while the video starts. */
    val videoPosterPath: String = "",
    /** The video's size as displayed (after rotation), for cropping it to fill. */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** Photo or video zoom, 1 = fills the screen. */
    val photoScale: Float = 1f,
    /** Photo or video shift as a fraction of the screen width / height. */
    val photoOffsetX: Float = 0f,
    val photoOffsetY: Float = 0f,
    /** Darkening over the background, 0..0.8, so text stays readable. */
    val dim: Float = 0.25f,
    /** Background blur, 0..1. Only shown on Android 12 and newer. */
    val blur: Float = 0f
) {
    val hasVideo: Boolean get() = videoPath.isNotBlank()
    val hasMedia: Boolean get() = videoPath.isNotBlank() || photoPath.isNotBlank()
}

@JsonClass(generateAdapter = true)
data class TextLayer(
    val id: String,
    val text: String,
    val fontId: String = "montserrat",
    /** ARGB colour. */
    val colorArgb: Long = 0xFFFFFFFF,
    /** Text height as a fraction of the screen width. */
    val sizeFraction: Float = 0.06f,
    /** Centre of the text as a fraction of the screen width / height. */
    val x: Float = 0.5f,
    val y: Float = 0.45f,
    /** Degrees, clockwise. */
    val rotation: Float = 0f,
    /** Widest the text may get before wrapping, as a fraction of the screen width. */
    val widthFraction: Float = 0.86f,
    val align: TextLayerAlign = TextLayerAlign.CENTER,
    val style: TextLayerStyle = TextLayerStyle.SHADOW
)

enum class TextLayerAlign { LEFT, CENTER, RIGHT }

enum class TextLayerStyle { NONE, SHADOW, HIGHLIGHT, OUTLINE }

/**
 * Live values that text can include. Typing {goal}, {app} or {time} in a text box
 * shows the current goal, the blocked app's name or the session time left.
 */
data class PauseTokens(
    val goal: String,
    val app: String,
    val timeLeft: String?
) {
    fun resolve(text: String): String = text
        .replace("{goal}", goal.ifBlank { "your goal" }, ignoreCase = true)
        .replace("{app}", app, ignoreCase = true)
        .replace("{time}", timeLeft ?: "", ignoreCase = true)
        .trim()

    companion object {
        const val GOAL = "{goal}"
        const val APP = "{app}"
        const val TIME = "{time}"
    }
}
