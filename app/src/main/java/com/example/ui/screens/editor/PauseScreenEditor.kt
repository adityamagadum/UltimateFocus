package com.example.ui.screens.editor

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import com.example.data.local.PauseVideoStore
import com.example.ui.pause.PausePreview
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FocusGuardApplication
import com.example.data.local.PauseImageStore
import com.example.data.model.BlockMode
import com.example.data.model.PauseBackground
import com.example.data.model.PauseDesign
import com.example.data.model.PauseTokens
import com.example.data.model.TextLayer
import com.example.data.model.TextLayerAlign
import com.example.data.model.TextLayerStyle
import com.example.ui.intervention.BlockInfo
import com.example.ui.intervention.InterventionActivity
import com.example.ui.pause.CanvasEditor
import com.example.ui.pause.MAX_SIZE
import com.example.ui.pause.MIN_SIZE
import com.example.ui.pause.PauseBackgrounds
import com.example.ui.pause.PauseCanvas
import com.example.ui.pause.PauseChrome
import com.example.ui.pause.PauseFonts
import com.example.ui.pause.PausePrompts
import com.example.ui.pause.PauseTemplates
import com.example.ui.pause.PauseTextColors
import com.example.ui.pause.PresetGradient
import com.example.ui.pause.ScaledScreenBox
import com.example.ui.pause.newTextLayer
import com.example.ui.theme.FocusOnPrimaryContainer
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusOutlineVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusPrimaryContainer
import com.example.ui.theme.FocusSurface
import com.example.ui.theme.FocusSurfaceContainer
import com.example.ui.theme.FocusSurfaceContainerHigh
import com.example.ui.theme.FocusSurfaceContainerHighest
import com.example.viewmodel.FocusGuardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private enum class EditorTab(val label: String, val icon: ImageVector) {
    TEMPLATES("Templates", Icons.Default.AutoAwesome),
    BACKGROUND("Background", Icons.Default.Wallpaper),
    TEXT("Text", Icons.Default.TextFields),
    MESSAGES("Messages", Icons.Default.FormatQuote)
}

/** Panel height stays fixed, so switching tabs never makes the preview jump. */
private val PanelHeight = 236.dp

/** How the pause screen looks in the editor's preview. */
private val PreviewBlock = BlockInfo(
    appName = "YouTube",
    packageName = "com.google.android.youtube",
    blockMode = BlockMode.CONTENT_LEVEL,
    ruleLabel = "Shorts"
)

/**
 * The design being edited, with undo and redo.
 *
 * During a drag every frame updates [design] directly; only the finished gesture is
 * recorded for undo, so one drag is one undo step.
 */
@Stable
internal class PauseEditorState(initial: PauseDesign) {
    var design by mutableStateOf(initial)
        private set
    var selectedId by mutableStateOf<String?>(null)

    /** The design as last saved, to tell whether there is anything to save. */
    private var savedDesign by mutableStateOf(initial)
    val hasUnsavedChanges: Boolean get() = design != savedDesign

    /** Shown over the preview while a photo or video is being added; null when idle. */
    var importStatus by mutableStateOf<String?>(null)

    fun markSaved() {
        savedDesign = design
    }

    private val undoStack = mutableStateListOf<PauseDesign>()
    private val redoStack = mutableStateListOf<PauseDesign>()
    private var gestureStart: PauseDesign? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val selectedLayer: TextLayer? get() = design.layers.firstOrNull { it.id == selectedId }

    fun commit(next: PauseDesign) {
        if (next == design) return
        pushUndo(design)
        design = next
    }

    /** A frame of a gesture or slider drag: shown immediately, recorded at the end. */
    fun live(next: PauseDesign) {
        if (gestureStart == null) gestureStart = design
        design = next
    }

    fun beginGesture() {
        if (gestureStart == null) gestureStart = design
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != design) pushUndo(start)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(design)
        design = undoStack.removeAt(undoStack.lastIndex)
        dropMissingSelection()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(design)
        design = redoStack.removeAt(redoStack.lastIndex)
        dropMissingSelection()
    }

    fun updateLayer(id: String, live: Boolean = false, change: (TextLayer) -> TextLayer) {
        val next = design.copy(layers = design.layers.map { if (it.id == id) change(it) else it })
        if (live) live(next) else commit(next)
    }

    fun updateBackground(live: Boolean = false, change: (PauseBackground) -> PauseBackground) {
        val next = design.copy(background = change(design.background))
        if (live) live(next) else commit(next)
    }

    fun addLayer(layer: TextLayer) {
        commit(design.copy(layers = design.layers + layer))
        selectedId = layer.id
    }

    fun removeLayer(id: String) {
        commit(design.copy(layers = design.layers.filterNot { it.id == id }))
        if (selectedId == id) selectedId = null
    }

    private fun pushUndo(previous: PauseDesign) {
        undoStack.add(previous)
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        redoStack.clear()
    }

    private fun dropMissingSelection() {
        if (selectedId != null && design.layers.none { it.id == selectedId }) selectedId = null
    }

    private companion object {
        const val MAX_UNDO = 40
    }
}

/**
 * Keeps the editor's work (and its undo history) alive across screen rotation and
 * trips to the photo or video picker. Scoped to the editor screen: leaving the editor
 * clears it.
 */
internal class PauseEditorHolder : ViewModel() {
    var state: PauseEditorState? = null

    /** Compression in progress, kept here so rotating the phone doesn't cancel it. */
    var importJob: Job? = null

    override fun onCleared() {
        importJob?.cancel()
    }
}

/**
 * Edits the pause screen: background (preset, photo or video), text anywhere, fonts,
 * ready-made messages and templates. Nothing is saved until Save is tapped; leaving with
 * unsaved changes asks first.
 */
@Composable
fun PauseScreenEditor(
    viewModel: FocusGuardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.interventionConfig.collectAsStateWithLifecycle()
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()

    val holder: PauseEditorHolder = viewModel()
    val editor = holder.state ?: PauseEditorState(config.resolvedDesign()).also { holder.state = it }
    var tab by rememberSaveable { mutableStateOf(EditorTab.TEMPLATES) }
    var editingLayerId by remember { mutableStateOf<String?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    val tokens = PauseTokens(goal = dailyStats.todayGoalText, app = PreviewBlock.appName, timeLeft = "18:42")

    fun save() {
        val design = editor.design
        viewModel.updateInterventionConfig(
            config.copy(
                design = design,
                // Older readers of the config still see the main line.
                customQuote = design.layers.firstOrNull()?.text ?: config.customQuote,
                subMessage = "",
                anchorImageUri = ""
            )
        )
        editor.markSaved()
        // Removes photos and videos no longer used. Runs on the app's own scope so it
        // still finishes after this screen closes.
        val keep = setOf(
            design.background.photoPath,
            design.background.videoPath,
            design.background.videoPosterPath
        )
        val appContext = context.applicationContext
        FocusGuardApplication.container()?.applicationScope?.launch(Dispatchers.IO) {
            PauseImageStore.cleanup(appContext, keep)
        }
    }

    fun leave() {
        if (editor.hasUnsavedChanges) showLeaveDialog = true else onBack()
    }

    BackHandler { leave() }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        holder.importJob?.cancel()
        holder.importJob = holder.viewModelScope.launch {
            editor.importStatus = "Adding photo…"
            val path = withContext(Dispatchers.IO) { PauseImageStore.importBackground(context.applicationContext, uri) }
            editor.importStatus = null
            if (path == null) {
                Toast.makeText(context.applicationContext, "Couldn't open that photo", Toast.LENGTH_SHORT).show()
            } else {
                editor.updateBackground {
                    it.copy(
                        photoPath = path,
                        videoPath = "",
                        videoPosterPath = "",
                        photoScale = 1f,
                        photoOffsetX = 0f,
                        photoOffsetY = 0f,
                        dim = max(it.dim, 0.3f)
                    )
                }
            }
        }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        holder.importJob?.cancel()
        holder.importJob = holder.viewModelScope.launch {
            editor.importStatus = "Compressing video… 0%"
            val video = PauseVideoStore.import(context.applicationContext, uri) { percent ->
                editor.importStatus = "Compressing video… $percent%"
            }
            editor.importStatus = null
            if (video == null) {
                Toast.makeText(context.applicationContext, "Couldn't use that video", Toast.LENGTH_SHORT).show()
            } else {
                editor.updateBackground {
                    it.copy(
                        videoPath = video.videoPath,
                        videoPosterPath = video.posterPath,
                        videoWidth = video.width,
                        videoHeight = video.height,
                        photoPath = "",
                        photoScale = 1f,
                        photoOffsetX = 0f,
                        photoOffsetY = 0f,
                        dim = max(it.dim, 0.3f)
                    )
                }
            }
        }.also { job ->
            job.invokeOnCompletion { if (job.isCancelled) editor.importStatus = null }
        }
    }

    fun startNewText(text: String = "") {
        val y = 0.3f + 0.08f * (editor.design.layers.size % 5)
        val layer = newTextLayer(text, y)
        editor.addLayer(layer)
        tab = EditorTab.TEXT
        if (text.isBlank()) {
            editingIsNew = true
            editingLayerId = layer.id
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FocusSurface)
            .navigationBarsPadding()
    ) {
        EditorTopBar(
            canUndo = editor.canUndo,
            canRedo = editor.canRedo,
            hasUnsavedChanges = editor.hasUnsavedChanges,
            onBack = { leave() },
            onUndo = { editor.undo() },
            onRedo = { editor.redo() },
            onPreview = {
                // Shows the current draft without saving it.
                PausePreview.design = editor.design
                context.startActivity(InterventionActivity.previewIntent(context))
            },
            onSave = {
                save()
                Toast.makeText(context.applicationContext, "Pause screen saved", Toast.LENGTH_SHORT).show()
            }
        )

        // ---------------------------------------------------------------- preview
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val screen = LocalConfiguration.current
            val fullWidth = screen.screenWidthDp.dp
            val fullHeight = (screen.screenHeightDp + 48).dp // + system bars, roughly
            val scale = min(maxWidth / fullWidth, maxHeight / fullHeight)

            ScaledScreenBox(
                fullWidth = fullWidth,
                fullHeight = fullHeight,
                scale = scale,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, FocusOutlineVariant, RoundedCornerShape(22.dp))
                    .testTag("pause_editor_canvas")
            ) {
                PauseCanvas(
                    design = editor.design,
                    tokens = tokens,
                    modifier = Modifier.fillMaxSize(),
                    editor = CanvasEditor(
                        selectedId = editor.selectedId,
                        layersInteractive = tab != EditorTab.BACKGROUND,
                        photoInteractive = tab == EditorTab.BACKGROUND && editor.design.background.hasMedia,
                        onSelect = { id ->
                            editor.selectedId = id
                            if (id != null && tab == EditorTab.TEMPLATES) tab = EditorTab.TEXT
                        },
                        onEditText = { id ->
                            editingIsNew = false
                            editingLayerId = id
                        },
                        onLayerChange = { layer -> editor.updateLayer(layer.id, live = true) { layer } },
                        onBackgroundChange = { bg -> editor.updateBackground(live = true) { bg } },
                        onGestureStart = { editor.beginGesture() },
                        onGestureEnd = { editor.endGesture() }
                    )
                )
                PauseChrome(
                    block = PreviewBlock,
                    sessionTimeLeft = null,
                    closeLabel = "Back to YouTube",
                    showBlockInfo = editor.design.showBlockInfo,
                    topInset = 28.dp,
                    bottomInset = 20.dp,
                    interactive = false,
                    onClose = {}
                )
            }

            val status = editor.importStatus
            if (status != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 22.dp, vertical = 16.dp)
                ) {
                    CircularProgressIndicator(color = FocusPrimary, modifier = Modifier.size(32.dp))
                    Text(status, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    TextButton(onClick = {
                        holder.importJob?.cancel()
                        editor.importStatus = null
                    }) { Text("Cancel", color = FocusPrimary) }
                }
            }
        }

        EditorTabs(selected = tab, onSelect = { tab = it })

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
            label = "editorPanel",
            modifier = Modifier
                .fillMaxWidth()
                .height(PanelHeight)
        ) { current ->
            when (current) {
                EditorTab.TEMPLATES -> TemplatesPanel(
                    tokens = tokens,
                    onApply = { template ->
                        editor.commit(template.copy(showBlockInfo = editor.design.showBlockInfo))
                        editor.selectedId = null
                    }
                )
                EditorTab.BACKGROUND -> BackgroundPanel(
                    background = editor.design.background,
                    showBlockInfo = editor.design.showBlockInfo,
                    isBusy = editor.importStatus != null,
                    onPickPhoto = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onPickVideo = {
                        pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    onPreset = { id ->
                        editor.updateBackground { it.copy(presetId = id, photoPath = "", videoPath = "", videoPosterPath = "") }
                    },
                    onRemoveMedia = {
                        editor.updateBackground { it.copy(photoPath = "", videoPath = "", videoPosterPath = "") }
                    },
                    onDim = { value -> editor.updateBackground(live = true) { it.copy(dim = value) } },
                    onBlur = { value -> editor.updateBackground(live = true) { it.copy(blur = value) } },
                    onSlideEnd = { editor.endGesture() },
                    onShowBlockInfo = { show -> editor.commit(editor.design.copy(showBlockInfo = show)) }
                )
                EditorTab.TEXT -> TextPanel(
                    layer = editor.selectedLayer,
                    onAdd = { startNewText() },
                    onEdit = { id ->
                        editingIsNew = false
                        editingLayerId = id
                    },
                    onDuplicate = { layer ->
                        val copy = layer.copy(
                            id = "layer_" + System.nanoTime(),
                            y = (layer.y + 0.07f).coerceAtMost(0.95f)
                        )
                        editor.addLayer(copy)
                    },
                    onDelete = { id -> editor.removeLayer(id) },
                    onChange = { id, change -> editor.updateLayer(id, change = change) },
                    onChangeLive = { id, change -> editor.updateLayer(id, live = true, change = change) },
                    onSlideEnd = { editor.endGesture() }
                )
                EditorTab.MESSAGES -> MessagesPanel(
                    hasSelection = editor.selectedLayer != null,
                    onPick = { prompt ->
                        val selected = editor.selectedLayer
                        if (selected != null) {
                            editor.updateLayer(selected.id) { it.copy(text = prompt) }
                        } else {
                            startNewText(prompt)
                            tab = EditorTab.MESSAGES
                        }
                    }
                )
            }
        }
    }

    // Text editing dialog.
    val editingLayer = editor.design.layers.firstOrNull { it.id == editingLayerId }
    if (editingLayer != null) {
        EditTextDialog(
            initial = editingLayer.text,
            isNew = editingIsNew,
            onDone = { text ->
                if (text.isBlank()) {
                    editor.removeLayer(editingLayer.id)
                } else {
                    editor.updateLayer(editingLayer.id) { it.copy(text = text) }
                }
                editingLayerId = null
            },
            onDismiss = {
                if (editingIsNew && editingLayer.text.isBlank()) editor.removeLayer(editingLayer.id)
                editingLayerId = null
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = FocusSurfaceContainer,
            title = { Text("Save your changes?", color = FocusOnSurface) },
            text = {
                Text(
                    "Your pause screen has changes that aren't saved yet.",
                    color = FocusOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        save()
                        onBack()
                    },
                    modifier = Modifier.testTag("leave_save_button")
                ) { Text("Save", color = FocusPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showLeaveDialog = false }) {
                        Text("Keep editing", color = FocusOnSurfaceVariant)
                    }
                    TextButton(
                        onClick = {
                            showLeaveDialog = false
                            holder.importJob?.cancel()
                            onBack()
                        },
                        modifier = Modifier.testTag("leave_discard_button")
                    ) { Text("Discard", color = Color(0xFFFFB4AB)) }
                }
            }
        )
    }
}

// ===================================================================== top bar & tabs

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FocusOnSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Pause screen", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = FocusOnSurface)
            Text(
                if (hasUnsavedChanges) "Unsaved changes" else "All changes saved",
                fontSize = 11.sp,
                color = if (hasUnsavedChanges) Color(0xFFFFB95F) else FocusOnSurfaceVariant
            )
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) FocusOnSurface else FocusOnSurfaceVariant.copy(alpha = 0.35f)
            )
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) FocusOnSurface else FocusOnSurfaceVariant.copy(alpha = 0.35f)
            )
        }
        IconButton(onClick = onPreview, modifier = Modifier.testTag("preview_pause_button")) {
            Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = FocusOnSurface)
        }
        val saveBackground by animateColorAsState(
            if (hasUnsavedChanges) FocusPrimaryContainer else FocusSurfaceContainerHigh,
            tween(200),
            label = "saveBg"
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(saveBackground)
                .clickable(enabled = hasUnsavedChanges, onClick = onSave)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("save_pause_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (hasUnsavedChanges) Icons.Default.Save else Icons.Default.Check,
                contentDescription = null,
                tint = if (hasUnsavedChanges) FocusOnPrimaryContainer else FocusOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (hasUnsavedChanges) "Save" else "Saved",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasUnsavedChanges) FocusOnPrimaryContainer else FocusOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditorTabs(selected: EditorTab, onSelect: (EditorTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FocusSurfaceContainer)
            .padding(4.dp)
    ) {
        EditorTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val background by animateColorAsState(
                if (isSelected) FocusPrimaryContainer else FocusPrimaryContainer.copy(alpha = 0f),
                tween(200),
                label = "tabBg"
            )
            val content by animateColorAsState(
                if (isSelected) FocusOnPrimaryContainer else FocusOnSurfaceVariant,
                tween(200),
                label = "tabFg"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 7.dp)
                    .testTag("editor_tab_" + tab.name),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Text(tab.label, fontSize = 11.sp, color = content, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ======================================================================= panels

@Composable
private fun TemplatesPanel(tokens: PauseTokens, onApply: (PauseDesign) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        PanelHint("Tap a template, then change anything on it.")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(PauseTemplates.all, key = { it.id }) { template ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onApply(template.design) }
                        .padding(4.dp)
                        .testTag("template_" + template.id)
                ) {
                    val screen = LocalConfiguration.current
                    val fullWidth = screen.screenWidthDp.dp
                    val fullHeight = (screen.screenHeightDp + 48).dp
                    val scale = 150.dp / fullHeight
                    ScaledScreenBox(
                        fullWidth = fullWidth,
                        fullHeight = fullHeight,
                        scale = scale,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        PauseCanvas(design = template.design, tokens = tokens, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        template.name,
                        fontSize = 12.sp,
                        color = FocusOnSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundPanel(
    background: PauseBackground,
    showBlockInfo: Boolean,
    isBusy: Boolean,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onPreset: (String) -> Unit,
    onRemoveMedia: () -> Unit,
    onDim: (Float) -> Unit,
    onBlur: (Float) -> Unit,
    onSlideEnd: () -> Unit,
    onShowBlockInfo: (Boolean) -> Unit
) {
    val hasPhoto = background.photoPath.isNotBlank() && !background.hasVideo
    val hasVideo = background.hasVideo
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 12.dp)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "photo") {
                SwatchTile(
                    label = "Photo",
                    selected = hasPhoto,
                    onClick = { if (!isBusy) onPickPhoto() },
                    tag = "bg_photo"
                ) {
                    MediaTileContent(Icons.Default.AddPhotoAlternate)
                }
            }
            item(key = "video") {
                SwatchTile(
                    label = "Video",
                    selected = hasVideo,
                    onClick = { if (!isBusy) onPickVideo() },
                    tag = "bg_video"
                ) {
                    MediaTileContent(Icons.Default.VideoLibrary)
                }
            }
            items(PauseBackgrounds.all, key = { it.id }) { preset ->
                SwatchTile(
                    label = preset.name,
                    selected = !background.hasMedia && background.presetId == preset.id,
                    onClick = { onPreset(preset.id) },
                    tag = "bg_" + preset.id
                ) {
                    PresetGradient(preset, Modifier.fillMaxSize())
                }
            }
        }

        LabeledSlider("Darken", background.dim, 0f..0.8f, onDim, onSlideEnd)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && background.hasMedia) {
            LabeledSlider("Blur", background.blur, 0f..1f, onBlur, onSlideEnd)
        }

        if (background.hasMedia) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (hasVideo) {
                        "Pinch and drag the video on the preview. It loops without sound."
                    } else {
                        "Pinch and drag the photo on the preview."
                    },
                    fontSize = 12.sp,
                    color = FocusOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRemoveMedia) {
                    Text(if (hasVideo) "Remove video" else "Remove photo", color = FocusPrimary)
                }
            }
        } else {
            Text(
                "Videos are trimmed to ${PauseVideoStore.MAX_DURATION_MS / 1000} seconds, " +
                    "made smaller and muted, so the pause screen stays fast.",
                fontSize = 11.sp,
                color = FocusOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show what was blocked", fontSize = 14.sp, color = FocusOnSurface)
                Text("The small card at the top", fontSize = 11.sp, color = FocusOnSurfaceVariant)
            }
            Switch(
                checked = showBlockInfo,
                onCheckedChange = onShowBlockInfo,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FocusSurface,
                    checkedTrackColor = FocusPrimary,
                    uncheckedTrackColor = FocusSurfaceContainerHighest
                )
            )
        }
    }
}

@Composable
private fun MediaTileContent(icon: ImageVector) {
    Box(
        Modifier
            .fillMaxSize()
            .background(FocusSurfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = FocusOnSurface)
    }
}

@Composable
private fun TextPanel(
    layer: TextLayer?,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (TextLayer) -> Unit,
    onDelete: (String) -> Unit,
    onChange: (String, (TextLayer) -> TextLayer) -> Unit,
    onChangeLive: (String, (TextLayer) -> TextLayer) -> Unit,
    onSlideEnd: () -> Unit
) {
    if (layer == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(FocusPrimaryContainer)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
                    .testTag("add_text_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = FocusOnPrimaryContainer)
                Spacer(Modifier.width(6.dp))
                Text("Add text", fontWeight = FontWeight.Bold, color = FocusOnPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Tap text on the preview to select it. Drag to move, pinch to resize, " +
                    "twist to rotate, double-tap to change the words.",
                fontSize = 12.sp,
                color = FocusOnSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolButton("Edit", Icons.Default.Edit) { onEdit(layer.id) }
            ToolButton("Duplicate", Icons.Default.ContentCopy) { onDuplicate(layer) }
            ToolButton("Level", Icons.Default.Straighten) { onChange(layer.id) { it.copy(rotation = 0f) } }
            ToolButton("Add", Icons.Default.Add, onClick = onAdd)
            ToolButton("Delete", Icons.Default.Delete, tint = Color(0xFFFFB4AB)) { onDelete(layer.id) }
        }

        // Fonts, each shown in itself.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            items(PauseFonts.all, key = { it.id }) { font ->
                val selected = font.id == layer.fontId
                val context = LocalContext.current
                val family = remember(font.id) { PauseFonts.family(context, font.id) }
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) FocusSurfaceContainerHighest else FocusSurfaceContainer)
                        .border(
                            BorderStroke(1.5.dp, if (selected) FocusPrimary else Color.Transparent),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onChange(layer.id) { it.copy(fontId = font.id) } }
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .testTag("font_" + font.id),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Aa", fontFamily = family, fontWeight = PauseFonts.weightFor(font.id), fontSize = 22.sp, color = FocusOnSurface)
                    Text(font.name, fontSize = 10.sp, color = FocusOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Colours.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(PauseTextColors) { color ->
                val argb = color.toArgb().toLong() and 0xFFFFFFFFL
                val selected = argb == layer.colorArgb
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (selected) 2.5.dp else 1.dp,
                            if (selected) FocusPrimary else FocusOutlineVariant,
                            CircleShape
                        )
                        .clickable { onChange(layer.id) { it.copy(colorArgb = argb) } }
                )
            }
        }

        LabeledSlider(
            label = "Size",
            value = layer.sizeFraction,
            range = MIN_SIZE..MAX_SIZE,
            onChange = { value -> onChangeLive(layer.id) { it.copy(sizeFraction = value) } },
            onEnd = onSlideEnd
        )
        LabeledSlider(
            label = "Width",
            value = layer.widthFraction,
            range = 0.3f..0.95f,
            onChange = { value -> onChangeLive(layer.id) { it.copy(widthFraction = value) } },
            onEnd = onSlideEnd
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                TextLayerAlign.LEFT to Icons.AutoMirrored.Filled.FormatAlignLeft,
                TextLayerAlign.CENTER to Icons.Default.FormatAlignCenter,
                TextLayerAlign.RIGHT to Icons.AutoMirrored.Filled.FormatAlignRight
            ).forEach { (align, icon) ->
                val selected = layer.align == align
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) FocusSurfaceContainerHighest else Color.Transparent)
                        .clickable { onChange(layer.id) { it.copy(align = align) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = align.name, tint = if (selected) FocusPrimary else FocusOnSurfaceVariant)
                }
            }
            Spacer(Modifier.width(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TextLayerStyle.entries) { style ->
                    val selected = layer.style == style
                    Text(
                        text = when (style) {
                            TextLayerStyle.NONE -> "Plain"
                            TextLayerStyle.SHADOW -> "Shadow"
                            TextLayerStyle.HIGHLIGHT -> "Highlight"
                            TextLayerStyle.OUTLINE -> "Outline"
                        },
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) FocusOnPrimaryContainer else FocusOnSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) FocusPrimaryContainer else FocusSurfaceContainerHigh)
                            .clickable { onChange(layer.id) { it.copy(style = style) } }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesPanel(hasSelection: Boolean, onPick: (String) -> Unit) {
    var groupIndex by rememberSaveable { mutableStateOf(0) }
    val group = PausePrompts.groups[groupIndex]
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            items(PausePrompts.groups.size) { index ->
                val selected = index == groupIndex
                Text(
                    text = PausePrompts.groups[index].title,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) FocusOnPrimaryContainer else FocusOnSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) FocusPrimaryContainer else FocusSurfaceContainerHigh)
                        .clickable { groupIndex = index }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
        Text(
            text = group.why + if (hasSelection) " Tap one to replace the selected text." else " Tap one to add it.",
            fontSize = 11.sp,
            color = FocusOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(group.prompts) { prompt ->
                Text(
                    text = prompt,
                    fontSize = 14.sp,
                    color = FocusOnSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FocusSurfaceContainer)
                        .clickable { onPick(prompt) }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
        }
    }
}

// ======================================================================= pieces

@Composable
private fun PanelHint(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = FocusOnSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun SwatchTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 84.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) FocusPrimary else FocusOutlineVariant,
                    RoundedCornerShape(10.dp)
                )
        ) { content() }
        Text(label, fontSize = 10.sp, color = FocusOnSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, tint: Color = FocusOnSurface, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 10.sp, color = FocusOnSurfaceVariant)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = FocusOnSurfaceVariant, modifier = Modifier.width(52.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onEnd,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = FocusPrimary,
                activeTrackColor = FocusPrimary,
                inactiveTrackColor = FocusSurfaceContainerHighest
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EditTextDialog(
    initial: String,
    isNew: Boolean,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun insert(token: String) {
        val start = value.selection.min
        val end = value.selection.max
        val text = value.text.substring(0, start) + token + value.text.substring(end)
        if (text.length <= MAX_TEXT) value = TextFieldValue(text, TextRange(start + token.length))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusSurfaceContainer,
        title = { Text(if (isNew) "Add text" else "Edit text", color = FocusOnSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.text.length <= MAX_TEXT) value = it },
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FocusOnSurface,
                        unfocusedTextColor = FocusOnSurface,
                        focusedBorderColor = FocusPrimary,
                        unfocusedBorderColor = FocusSurfaceContainerHighest,
                        cursorColor = FocusPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("layer_text_field")
                )
                Text(
                    "Add live info. It updates by itself on the pause screen:",
                    fontSize = 11.sp,
                    color = FocusOnSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Goal" to PauseTokens.GOAL,
                        "App name" to PauseTokens.APP,
                        "Time left" to PauseTokens.TIME
                    ).forEach { (label, token) ->
                        AssistChip(
                            onClick = { insert(token) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = FocusOnSurface)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(value.text.trim()) }) { Text("Done", color = FocusPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FocusOnSurfaceVariant) }
        }
    )
}

private const val MAX_TEXT = 160
