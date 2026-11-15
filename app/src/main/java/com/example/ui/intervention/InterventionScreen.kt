package com.example.ui.intervention

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.data.model.BlockMode
import com.example.data.model.InterventionConfig
import com.example.data.model.PauseTokens
import com.example.ui.pause.PauseCanvas
import com.example.ui.pause.PauseChrome
import com.example.ui.theme.FocusSurface

/** Everything the pause screen needs to say about one block. */
data class BlockInfo(
    val appName: String,
    val packageName: String,
    val blockMode: BlockMode,
    val ruleLabel: String
)

/**
 * The full-screen pause shown over a blocked app.
 *
 * Draws the user's own design (background, text anywhere, fonts) from the editor, with
 * what was blocked at the top and one button that leaves at the bottom.
 */
@Composable
fun InterventionScreen(
    block: BlockInfo,
    config: InterventionConfig,
    goal: String,
    sessionTimeLeft: String?,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val design = remember(config) { config.resolvedDesign() }
    val tokens = PauseTokens(goal = goal, app = block.appName, timeLeft = sessionTimeLeft)
    val insets = WindowInsets.systemBars.asPaddingValues()

    // The text eases in over a fifth of a second, so the screen arrives softly rather
    // than snapping in. The background and close button are there from the first frame.
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { textAlpha.animateTo(1f, tween(220)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FocusSurface)
    ) {
        PauseCanvas(
            design = design,
            tokens = tokens,
            textAlpha = textAlpha.value,
            modifier = Modifier.fillMaxSize()
        )
        PauseChrome(
            block = block,
            sessionTimeLeft = sessionTimeLeft,
            closeLabel = closeLabel,
            showBlockInfo = design.showBlockInfo,
            topInset = insets.calculateTopPadding(),
            bottomInset = insets.calculateBottomPadding(),
            interactive = true,
            onClose = onClose
        )
    }
}
