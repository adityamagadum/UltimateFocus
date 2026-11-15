package com.example.ui.pause

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.AnchorPhotoStore
import com.example.data.local.PauseImageStore
import com.example.data.local.PauseVideoStore
import com.example.data.model.BlockMode
import com.example.data.model.PauseBackground
import com.example.data.model.PauseDesign
import com.example.data.model.PauseTokens
import com.example.data.model.TextLayer
import com.example.data.model.TextLayerAlign
import com.example.data.model.TextLayerStyle
import com.example.ui.components.AppIcon
import com.example.ui.intervention.BlockInfo
import com.example.ui.theme.FocusOnPrimaryContainer
import com.example.ui.theme.FocusPrimaryContainer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hooks the editor passes in so the canvas can be edited. Null on the real pause
 * screen, where nothing reacts to touch except the close button.
 */
class CanvasEditor(
    val selectedId: String?,
    /** Text can be tapped, dragged, pinched and rotated. */
    val layersInteractive: Boolean,
    /** The background photo can be dragged and pinched. */
    val photoInteractive: Boolean,
    val onSelect: (String?) -> Unit,
    val onEditText: (String) -> Unit,
    /** Called on every frame of a gesture with the updated value. */
    val onLayerChange: (TextLayer) -> Unit,
    val onBackgroundChange: (PauseBackground) -> Unit,
    val onGestureStart: () -> Unit,
    val onGestureEnd: () -> Unit
)

/**
 * Draws a pause-screen design: the background, then every text layer at its position.
 *
 * Used by both the real pause screen and the editor, so what you design is exactly
 * what appears over a blocked app.
 */
@Composable
fun PauseCanvas(
    design: PauseDesign,
    tokens: PauseTokens,
    modifier: Modifier = Modifier,
    editor: CanvasEditor? = null,
    textAlpha: Float = 1f
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        var showVerticalGuide by remember { mutableStateOf(false) }
        var showHorizontalGuide by remember { mutableStateOf(false) }

        val currentEditor by rememberUpdatedState(editor)
        val currentBackground by rememberUpdatedState(design.background)

        PauseBackgroundView(
            background = design.background,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (editor != null) {
                        Modifier
                            .pointerInput(editor.photoInteractive) {
                                if (!editor.photoInteractive) return@pointerInput
                                detectTransform(
                                    onStart = { currentEditor?.onGestureStart?.invoke() },
                                    onEnd = { currentEditor?.onGestureEnd?.invoke() }
                                ) { pan, zoom, _ ->
                                    val bg = currentBackground
                                    val scale = (bg.photoScale * zoom).coerceIn(1f, 4f)
                                    val maxOffset = (scale - 1f) / 2f
                                    currentEditor?.onBackgroundChange?.invoke(
                                        bg.copy(
                                            photoScale = scale,
                                            photoOffsetX = (bg.photoOffsetX + pan.x / widthPx).coerceIn(-maxOffset, maxOffset),
                                            photoOffsetY = (bg.photoOffsetY + pan.y / heightPx).coerceIn(-maxOffset, maxOffset)
                                        )
                                    )
                                }
                            }
                            // Tapping empty space clears the selection.
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { currentEditor?.onSelect?.invoke(null) })
                            }
                    } else Modifier
                )
        )

        Layout(
            content = {
                design.layers.forEach { layer ->
                    key(layer.id) {
                        TextLayerView(
                            layer = layer,
                            text = tokens.resolve(layer.text),
                            canvasWidthPx = widthPx,
                            canvasHeightPx = heightPx,
                            editor = editor,
                            alpha = textAlpha,
                            onGuides = { vertical, horizontal ->
                                showVerticalGuide = vertical
                                showHorizontalGuide = horizontal
                            }
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val placeables = measurables.mapIndexed { index, measurable ->
                val layer = design.layers[index]
                val maxW = (layer.widthFraction * w).roundToInt().coerceAtLeast(1)
                measurable.measure(Constraints(maxWidth = maxW))
            }
            layout(w, h) {
                placeables.forEachIndexed { index, placeable ->
                    val layer = design.layers[index]
                    placeable.place(
                        (layer.x * w - placeable.width / 2f).roundToInt(),
                        (layer.y * h - placeable.height / 2f).roundToInt()
                    )
                }
            }
        }

        // Centre guides, shown while text snaps to the middle.
        if (showVerticalGuide || showHorizontalGuide) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val guide = Color(0xCC4EDEA3)
                        val stroke = 1.dp.toPx()
                        if (showVerticalGuide) {
                            drawLine(guide, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), stroke)
                        }
                        if (showHorizontalGuide) {
                            drawLine(guide, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                        }
                    }
            )
        }
    }
}

/** A built-in gradient or a photo, with optional blur and darkening. */
@Composable
fun PauseBackgroundView(background: PauseBackground, modifier: Modifier = Modifier) {
    val preset = PauseBackgrounds.byId(background.presetId)
    val video = remember(background.videoPath) { PauseVideoStore.fileFor(background.videoPath) }
    val poster = remember(background.videoPosterPath) { PauseImageStore.fileFor(background.videoPosterPath) }
    val photo = remember(background.photoPath) {
        PauseImageStore.fileFor(background.photoPath) ?: AnchorPhotoStore.fileFor(background.photoPath)
    }
    val blur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && background.blur > 0f) {
        Modifier.blur((background.blur * 24f).dp)
    } else Modifier
    Box(modifier = modifier.clipToBounds()) {
        PresetGradient(preset, Modifier.fillMaxSize())
        if (video != null) {
            PauseVideoBackground(
                video = video,
                poster = poster,
                background = background,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blur)
            )
        } else if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = background.photoScale
                        scaleY = background.photoScale
                        translationX = background.photoOffsetX * size.width
                        translationY = background.photoOffsetY * size.height
                    }
                    .then(blur)
            )
        }
        if (background.dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = background.dim.coerceIn(0f, 0.85f)))
            )
        }
    }
}

@Composable
fun PresetGradient(preset: PresetBackground, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val linear = Brush.linearGradient(
                colors = preset.colors,
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.6f, size.height)
            )
            val glowRadius = size.maxDimension * 0.7f
            val glow = Brush.radialGradient(
                colors = listOf(preset.glow.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.1f),
                radius = glowRadius
            )
            onDrawBehind {
                drawRect(linear)
                drawRect(glow)
            }
        }
    )
}

@Composable
private fun TextLayerView(
    layer: TextLayer,
    text: String,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    editor: CanvasEditor?,
    alpha: Float,
    onGuides: (vertical: Boolean, horizontal: Boolean) -> Unit
) {
    val isSelected = editor?.selectedId == layer.id
    val currentLayer by rememberUpdatedState(layer)
    val currentEditor by rememberUpdatedState(editor)
    val density = LocalDensity.current
    val selectionPad = with(density) { 6.dp.toPx() }
    // Rotation is tracked unsnapped during a gesture so snapping to straight can't trap it.
    var rawRotation by remember { mutableFloatStateOf(layer.rotation) }

    val interactive = editor?.layersInteractive == true
    val gestureModifier = if (editor != null) {
        Modifier
            .pointerInput(layer.id, interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures(
                    // Selects on touch-down, so it feels instant.
                    onPress = { currentEditor?.onSelect?.invoke(layer.id) },
                    onDoubleTap = { currentEditor?.onEditText?.invoke(layer.id) }
                )
            }
            .pointerInput(layer.id, interactive) {
                if (!interactive) return@pointerInput
                detectTransform(
                    onStart = {
                        rawRotation = currentLayer.rotation
                        currentEditor?.onSelect?.invoke(layer.id)
                        currentEditor?.onGestureStart?.invoke()
                    },
                    onEnd = {
                        onGuides(false, false)
                        currentEditor?.onGestureEnd?.invoke()
                    }
                ) { pan, zoom, rotation ->
                    val current = currentLayer
                    // Touch movement arrives in the text's own (rotated) frame; turn it
                    // back into screen directions before moving.
                    val radians = current.rotation * PI.toFloat() / 180f
                    val dx = pan.x * cos(radians) - pan.y * sin(radians)
                    val dy = pan.x * sin(radians) + pan.y * cos(radians)
                    var x = (current.x + dx / canvasWidthPx).coerceIn(0.03f, 0.97f)
                    var y = (current.y + dy / canvasHeightPx).coerceIn(0.03f, 0.97f)
                    val snapV = abs(x - 0.5f) < SNAP
                    val snapH = abs(y - 0.5f) < SNAP
                    if (snapV) x = 0.5f
                    if (snapH) y = 0.5f
                    onGuides(snapV, snapH)

                    rawRotation += rotation
                    val nearest = (rawRotation / 90f).roundToInt() * 90f
                    val snappedRotation = if (abs(rawRotation - nearest) < 4f) nearest else rawRotation

                    currentEditor?.onLayerChange?.invoke(
                        current.copy(
                            x = x,
                            y = y,
                            sizeFraction = (current.sizeFraction * zoom).coerceIn(MIN_SIZE, MAX_SIZE),
                            rotation = snappedRotation
                        )
                    )
                }
            }
    } else Modifier

    Box(
        modifier = Modifier
            .graphicsLayer {
                rotationZ = layer.rotation
                this.alpha = alpha
            }
            .drawWithContent {
                drawContent()
                if (isSelected) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(-selectionPad, -selectionPad),
                        size = Size(size.width + selectionPad * 2, size.height + selectionPad * 2),
                        cornerRadius = CornerRadius(selectionPad * 1.5f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    )
                }
            }
            .then(gestureModifier)
    ) {
        LayerText(layer = layer, text = text, canvasWidthPx = canvasWidthPx)
    }
}

/** The text itself, in its font, colour, size and style. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun LayerText(layer: TextLayer, text: String, canvasWidthPx: Float, modifier: Modifier = Modifier) {
    if (text.isBlank()) {
        // Keeps an empty layer grabbable in the editor; invisible on the pause screen.
        Box(modifier.size(1.dp))
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val family = remember(layer.fontId) { PauseFonts.family(context, layer.fontId) }
    val weight = remember(layer.fontId, family) { PauseFonts.weightFor(layer.fontId) }
    val fontPx = layer.sizeFraction * canvasWidthPx
    val fontSize = with(density) { fontPx.toSp() }
    val color = Color(layer.colorArgb.toInt())
    val contrast = if (color.luminance() > 0.5f) Color.Black else Color.White
    val textAlign = when (layer.align) {
        TextLayerAlign.LEFT -> TextAlign.Left
        TextLayerAlign.CENTER -> TextAlign.Center
        TextLayerAlign.RIGHT -> TextAlign.Right
    }
    val base = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = fontSize,
        lineHeight = fontSize * 1.18f,
        textAlign = textAlign,
        color = color
    )

    when (layer.style) {
        TextLayerStyle.NONE -> Text(text = text, style = base, modifier = modifier)
        TextLayerStyle.SHADOW -> Text(
            text = text,
            style = base.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = if (contrast == Color.White) 0.35f else 0.6f),
                    offset = Offset(0f, fontPx * 0.05f),
                    blurRadius = fontPx * 0.3f
                )
            ),
            modifier = modifier
        )
        TextLayerStyle.HIGHLIGHT -> Text(
            text = text,
            style = base,
            modifier = modifier
                .background(
                    contrast.copy(alpha = if (contrast == Color.Black) 0.55f else 0.88f),
                    RoundedCornerShape(with(density) { (fontPx * 0.25f).toDp() })
                )
                .padding(
                    horizontal = with(density) { (fontPx * 0.35f).toDp() },
                    vertical = with(density) { (fontPx * 0.12f).toDp() }
                )
        )
        TextLayerStyle.OUTLINE -> Box(modifier) {
            Text(
                text = text,
                style = base.copy(
                    color = contrast,
                    drawStyle = Stroke(width = fontPx * 0.12f, join = StrokeJoin.Round)
                )
            )
            Text(text = text, style = base)
        }
    }
}

/**
 * The fixed parts of the pause screen: what was blocked at the top, the close button at
 * the bottom. The close button is always there and always on top, so a design can never
 * hide the way out.
 */
@Composable
fun PauseChrome(
    block: BlockInfo,
    sessionTimeLeft: String?,
    closeLabel: String,
    showBlockInfo: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    interactive: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showBlockInfo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = block.packageName, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = blockHeadline(block),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = blockDetail(block),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (sessionTimeLeft != null) {
            Text(
                text = "Focus session: $sessionTimeLeft left",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(FocusPrimaryContainer)
                .then(if (interactive) Modifier.clickable(onClick = onClose) else Modifier)
                .testTag("close_intervention_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = closeLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = FocusOnPrimaryContainer
            )
        }
    }
}

/**
 * Lays out [content] at full phone-screen size, then shrinks it to fit.
 *
 * The editor uses this so the preview is laid out exactly like the real screen; touch
 * positions inside are still reported at full size, so gestures stay accurate.
 */
@Composable
fun ScaledScreenBox(
    fullWidth: Dp,
    fullHeight: Dp,
    scale: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.size(fullWidth * scale, fullHeight * scale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .requiredSize(fullWidth, fullHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            content()
        }
    }
}

internal fun blockHeadline(block: BlockInfo): String = when (block.blockMode) {
    BlockMode.CONTENT_LEVEL -> "Not this part of " + block.appName
    BlockMode.HARD_BLOCK -> block.appName + " is blocked"
    BlockMode.DAILY_ALLOWANCE -> "Today's time on " + block.appName + " is up"
}

internal fun blockDetail(block: BlockInfo): String = when (block.blockMode) {
    BlockMode.CONTENT_LEVEL ->
        block.ruleLabel.substringBefore(" •").trim() + " is blocked. The rest of the app still works."
    BlockMode.HARD_BLOCK -> "You chose to keep this app closed right now."
    BlockMode.DAILY_ALLOWANCE -> "Your limit is " + block.ruleLabel + ". It resets at midnight."
}

private const val SNAP = 0.015f
internal const val MIN_SIZE = 0.025f
internal const val MAX_SIZE = 0.2f

/**
 * Drag, pinch and twist, reported only once the finger has really moved, so a simple
 * tap or double-tap on text is never mistaken for a drag.
 */
internal suspend fun PointerInputScope.detectTransform(
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onGesture: (pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var pastSlop = false
        var started = false
        var zoomTotal = 1f
        var rotationTotal = 0f
        var panTotal = Offset.Zero
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break
            val zoom = event.calculateZoom()
            val rotation = event.calculateRotation()
            val pan = event.calculatePan()
            if (!pastSlop) {
                zoomTotal *= zoom
                rotationTotal += rotation
                panTotal += pan
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1 - zoomTotal) * centroidSize
                val rotationMotion = abs(rotationTotal * PI.toFloat() * centroidSize / 180f)
                if (zoomMotion > slop || rotationMotion > slop || panTotal.getDistance() > slop) {
                    pastSlop = true
                }
            }
            if (pastSlop) {
                if (!started) {
                    started = true
                    onStart()
                }
                if (zoom != 1f || rotation != 0f || pan != Offset.Zero) onGesture(pan, zoom, rotation)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (started) onEnd()
    }
}
