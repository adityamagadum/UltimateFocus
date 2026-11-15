package com.example.ui.pause

import androidx.compose.ui.graphics.Color
import com.example.data.model.PauseBackground
import com.example.data.model.PauseDesign
import com.example.data.model.TextLayer
import com.example.data.model.TextLayerAlign
import com.example.data.model.TextLayerStyle

/**
 * A built-in background, drawn in code so it adds nothing to the app's size.
 *
 * The greens and blues are deliberate: in a University of Melbourne study, a 40-second
 * look at a green scene measurably restored attention, so calm nature colours suit a
 * pause screen better than loud ones.
 */
data class PresetBackground(
    val id: String,
    val name: String,
    val colors: List<Color>,
    /** Soft light in the upper corner, for depth. */
    val glow: Color
)

object PauseBackgrounds {
    val all: List<PresetBackground> = listOf(
        PresetBackground("forest", "Forest", listOf(Color(0xFF0B2B1F), Color(0xFF14532D), Color(0xFF052E16)), Color(0xFF4ADE80)),
        PresetBackground("meadow", "Meadow", listOf(Color(0xFF134E4A), Color(0xFF3F6212), Color(0xFF1A2E05)), Color(0xFFBEF264)),
        PresetBackground("ocean", "Ocean", listOf(Color(0xFF0C4A6E), Color(0xFF075985), Color(0xFF082F49)), Color(0xFF38BDF8)),
        PresetBackground("lagoon", "Lagoon", listOf(Color(0xFF115E59), Color(0xFF0E7490), Color(0xFF083344)), Color(0xFF5EEAD4)),
        PresetBackground("dusk", "Dusk", listOf(Color(0xFF1E1B4B), Color(0xFF6D28D9), Color(0xFFEA580C)), Color(0xFFFDBA74)),
        PresetBackground("sunrise", "Sunrise", listOf(Color(0xFFFDE68A), Color(0xFFFB923C), Color(0xFFBE185D)), Color(0xFFFFFBEB)),
        PresetBackground("night", "Night sky", listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF1E293B)), Color(0xFF818CF8)),
        PresetBackground("mint", "Mint", listOf(Color(0xFF064E3B), Color(0xFF10B981), Color(0xFF022C22)), Color(0xFF6FFBBE)),
        PresetBackground("ember", "Ember", listOf(Color(0xFF0A0A0A), Color(0xFF450A0A), Color(0xFF7C2D12)), Color(0xFFF97316)),
        PresetBackground("lavender", "Lavender", listOf(Color(0xFF2E1065), Color(0xFF7E22CE), Color(0xFFF0ABFC)), Color(0xFFF5D0FE)),
        PresetBackground("sand", "Sand", listOf(Color(0xFFFEF3C7), Color(0xFFE7D3B0), Color(0xFFC8A97E)), Color(0xFFFFFFFF)),
        PresetBackground("slate", "Slate", listOf(Color(0xFF101418), Color(0xFF1F2937), Color(0xFF0B0E13)), Color(0xFF4EDEA3))
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): PresetBackground = byId[id] ?: byId.getValue("forest")
}

/** Colours offered for text. */
val PauseTextColors: List<Color> = listOf(
    Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF4EDEA3), Color(0xFFFFB95F),
    Color(0xFFFDE68A), Color(0xFFF87171), Color(0xFFF472B6), Color(0xFFA78BFA),
    Color(0xFF60A5FA), Color(0xFF5EEAD4), Color(0xFFE1E2E9), Color(0xFF1F2937)
)

/** A group of ready-made messages, with the research it is based on. */
data class PromptGroup(val title: String, val why: String, val prompts: List<String>)

object PausePrompts {
    val groups: List<PromptGroup> = listOf(
        PromptGroup(
            title = "Ask why",
            why = "Pausing to ask \"why am I opening this?\" cut app opens by 57% in a 6-week study (PNAS, 2023).",
            prompts = listOf(
                "Why am I opening {app} right now?",
                "Is this what you meant to do?",
                "What were you about to do before this?",
                "Do I want this, or is it just a habit?",
                "Bored, tired, or avoiding something?",
                "Will this matter in an hour?"
            )
        ),
        PromptGroup(
            title = "If-then plans",
            why = "\"If X, then I will Y\" plans beat plain goals across 642 tests. Name what to do instead, not what to avoid.",
            prompts = listOf(
                "If I want to scroll, then I take 3 slow breaths.",
                "If I open {app}, then I do 5 minutes of {goal} first.",
                "If I'm bored, then I stand up and drink water.",
                "If I'm stuck, then I write the next tiny step.",
                "If I reach for my phone, then I read one page.",
                "If it's late, then I put the phone across the room."
            )
        ),
        PromptGroup(
            title = "Your goal",
            why = "Seeing your own goal at the moment of temptation reminds you what you chose.",
            prompts = listOf(
                "{goal}",
                "You said: {goal}",
                "Future you is counting on this hour.",
                "One focused hour beats a scrolled day.",
                "Small steps today. Big change this year.",
                "Back to {goal}."
            )
        ),
        PromptGroup(
            title = "Breathe",
            why = "A short breath before deciding is the core of the pause that worked in the study above.",
            prompts = listOf(
                "Pause. Breathe. Choose.",
                "Breathe in 4. Hold 4. Out 6.",
                "Three slow breaths, then decide.",
                "The urge passes in about a minute.",
                "Calm first. Then choose."
            )
        )
    )
}

/** Ready-made designs, applied with one tap and then fully editable. */
data class PauseTemplate(val id: String, val name: String, val design: PauseDesign)

object PauseTemplates {
    val all: List<PauseTemplate> = listOf(
        PauseTemplate(
            "breathe", "Forest calm",
            PauseDesign(
                background = PauseBackground(presetId = "forest", dim = 0.1f),
                layers = listOf(
                    TextLayer("t1", "Pause. Breathe. Choose.", fontId = "playfair", sizeFraction = 0.085f, y = 0.42f),
                    TextLayer("t2", "Back to {goal}.", fontId = "montserrat", sizeFraction = 0.04f, y = 0.55f, colorArgb = 0xFFBBF7D0)
                )
            )
        ),
        PauseTemplate(
            "why", "Bold why",
            PauseDesign(
                background = PauseBackground(presetId = "ember", dim = 0.1f),
                layers = listOf(
                    TextLayer("t1", "Why am I opening {app}?", fontId = "bebas", sizeFraction = 0.13f, y = 0.4f, widthFraction = 0.8f, style = TextLayerStyle.NONE),
                    TextLayer("t2", "Close it. Get back to {goal}.", fontId = "montserrat", sizeFraction = 0.04f, y = 0.56f, colorArgb = 0xFFFDBA74, style = TextLayerStyle.NONE)
                )
            )
        ),
        PauseTemplate(
            "ifthen", "If-then plan",
            PauseDesign(
                background = PauseBackground(presetId = "ocean", dim = 0.1f),
                layers = listOf(
                    TextLayer("t1", "If I want to scroll,", fontId = "lora", sizeFraction = 0.065f, y = 0.4f),
                    TextLayer("t2", "then I take 3 slow breaths.", fontId = "lora", sizeFraction = 0.065f, y = 0.48f, colorArgb = 0xFF7DD3FC)
                )
            )
        ),
        PauseTemplate(
            "note", "Handwritten",
            PauseDesign(
                background = PauseBackground(presetId = "sand", dim = 0f),
                layers = listOf(
                    TextLayer("t1", "Future you is counting on this hour.", fontId = "caveat", sizeFraction = 0.095f, y = 0.44f, colorArgb = 0xFF1F2937, rotation = -4f, style = TextLayerStyle.NONE),
                    TextLayer("t2", "{goal}", fontId = "poppins", sizeFraction = 0.04f, y = 0.58f, colorArgb = 0xFF7C2D12, style = TextLayerStyle.NONE)
                )
            )
        ),
        PauseTemplate(
            "minimal", "Minimal",
            PauseDesign(
                background = PauseBackground(presetId = "slate", dim = 0f),
                layers = listOf(
                    TextLayer("t1", "Is this what you meant to do?", fontId = "poppins", sizeFraction = 0.062f, y = 0.44f, style = TextLayerStyle.NONE),
                    TextLayer("t2", "Your goal: {goal}", fontId = "montserrat", sizeFraction = 0.038f, y = 0.54f, colorArgb = 0xFF4EDEA3, style = TextLayerStyle.NONE)
                )
            )
        )
    )
}

/** Keeps a new layer's alignment and style sensible. */
internal fun newTextLayer(text: String, y: Float = 0.5f): TextLayer = TextLayer(
    id = "layer_" + System.nanoTime(),
    text = text,
    fontId = "montserrat",
    sizeFraction = 0.06f,
    y = y,
    align = TextLayerAlign.CENTER,
    style = TextLayerStyle.SHADOW
)
