package com.example.ui.screens.sessions

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ContentCatalog
import com.example.data.model.ProfileBlock
import com.example.data.model.QuickBlock
import com.example.data.model.SessionLock
import com.example.data.model.SessionProfile
import com.example.data.repository.InstalledApp
import com.example.ui.components.AppIcon
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
import com.example.ui.theme.FocusTertiary
import com.example.viewmodel.FocusGuardViewModel

/**
 * Creates or edits a custom session: name, emoji, soft or hard lock, default length,
 * and what is blocked while it runs (whole apps, or parts like Shorts).
 *
 * [profileId] null creates a new session.
 */
@Composable
fun SessionEditorScreen(
    viewModel: FocusGuardViewModel,
    profileId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profiles by viewModel.sessionProfiles.collectAsStateWithLifecycle()
    val existing = remember(profileId) { profiles.firstOrNull { it.id == profileId } }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: SessionProfile.EMOJIS.first()) }
    var hardLock by rememberSaveable { mutableStateOf(existing?.lock == SessionLock.HARD) }
    var showPause by rememberSaveable { mutableStateOf(existing?.showPauseScreen ?: true) }
    var defaultMinutes by rememberSaveable { mutableIntStateOf(existing?.defaultMinutes ?: 60) }
    val blocks = remember { mutableStateListOf<ProfileBlock>().apply { addAll(existing?.blocks.orEmpty()) } }

    var showAppPicker by remember { mutableStateOf(false) }
    var choosingFor by remember { mutableStateOf<Pair<String, String>?>(null) } // package, app name
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    // Locked while a hard-locked session (or Strict Mode) is holding. Read through
    // derivedStateOf so the per-second session tick doesn't redraw this screen.
    val activeSession = viewModel.activeSession.collectAsStateWithLifecycle()
    val strict = viewModel.isStrictModeEnabled.collectAsStateWithLifecycle()
    val isLocked by remember {
        derivedStateOf {
            activeSession.value?.let { it.isRunning && (it.hardLock || (strict.value && !it.isPaused)) } == true
        }
    }

    fun buildProfile(): SessionProfile = SessionProfile(
        id = existing?.id ?: ("profile_" + System.currentTimeMillis()),
        name = name.trim().ifBlank { "My session" },
        emoji = emoji,
        lock = if (hardLock) SessionLock.HARD else SessionLock.SOFT,
        blocks = blocks.toList(),
        showPauseScreen = showPause,
        defaultMinutes = defaultMinutes
    )

    val hasChanges = existing?.let { buildProfile() != it }
        ?: (name.isNotBlank() || blocks.isNotEmpty())

    fun save() {
        if (viewModel.saveSessionProfile(buildProfile())) {
            onBack()
        } else {
            Toast.makeText(context, "Locked until the current session ends", Toast.LENGTH_SHORT).show()
        }
    }

    fun leave() {
        if (hasChanges && !isLocked) showLeaveConfirm = true else onBack()
    }

    BackHandler { leave() }

    fun addOrMerge(block: ProfileBlock) {
        val index = blocks.indexOfFirst { it.packageName == block.packageName }
        if (index >= 0) blocks[index] = block else blocks.add(block)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FocusSurface)
            .navigationBarsPadding()
    ) {
        // ------------------------------------------------------------- top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { leave() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FocusOnSurface)
            }
            Text(
                if (existing == null) "New session" else "Edit session",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = FocusOnSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Save",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLocked) FocusOnSurfaceVariant else FocusOnPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isLocked) FocusSurfaceContainerHigh else FocusPrimaryContainer)
                    .clickable(enabled = !isLocked) { save() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
                    .testTag("save_session_profile")
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (isLocked) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(FocusTertiary.copy(alpha = 0.12f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = FocusTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "A locked session is running. Sessions can be changed once it ends.",
                            fontSize = 13.sp,
                            color = FocusOnSurface
                        )
                    }
                }
            }

            // ------------------------------------------------------- name + emoji
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Session name") },
                    placeholder = { Text("Kid session") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_name_field")
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SessionProfile.EMOJIS) { option ->
                        val selected = option == emoji
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (selected) FocusPrimaryContainer.copy(alpha = 0.3f) else FocusSurfaceContainer)
                                .border(if (selected) 2.dp else 0.dp, if (selected) FocusPrimary else Color.Transparent, CircleShape)
                                .clickable { emoji = option },
                            contentAlignment = Alignment.Center
                        ) { Text(option, fontSize = 22.sp) }
                    }
                }
            }

            // ------------------------------------------------------- lock
            item {
                SectionTitle("Lock")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LockOption(
                        title = "Soft lock",
                        description = "Can be paused or stopped any time.",
                        icon = Icons.Default.LockOpen,
                        selected = !hardLock,
                        onClick = { hardLock = false },
                        modifier = Modifier.weight(1f)
                    )
                    LockOption(
                        title = "Hard lock",
                        description = "Can't be paused or stopped until the time is up.",
                        icon = Icons.Default.Lock,
                        selected = hardLock,
                        onClick = { hardLock = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ------------------------------------------------------- length
            item {
                SectionTitle("Usual length")
                DurationChips(selected = defaultMinutes, onSelect = { defaultMinutes = it })
                Text(
                    "You can pick a different length each time you start it.",
                    fontSize = 11.sp,
                    color = FocusOnSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ------------------------------------------------------- blocks
            item {
                SectionTitle("What's blocked")
                val quickOptions = QuickBlock.ALL.filter { quick ->
                    blocks.none { b ->
                        b.packageName == quick.packageName && (b.wholeApp || quick.contentId in b.contentIds)
                    }
                }
                if (quickOptions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickOptions, key = { it.contentId }) { quick ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(FocusSurfaceContainerHigh)
                                    .clickable {
                                        val current = blocks.firstOrNull { it.packageName == quick.packageName }
                                        addOrMerge(
                                            ProfileBlock(
                                                packageName = quick.packageName,
                                                appName = quick.appName,
                                                wholeApp = false,
                                                contentIds = (current?.contentIds.orEmpty() + quick.contentId).distinct()
                                            )
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = FocusPrimary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(quick.title, fontSize = 12.sp, color = FocusOnSurface)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            items(blocks.toList(), key = { it.packageName }) { block ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(FocusSurfaceContainer)
                        .clickable { choosingFor = block.packageName to block.appName }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(packageName = block.packageName, size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(block.appName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FocusOnSurface)
                        Text(
                            if (block.wholeApp) "Whole app blocked" else block.label + " blocked",
                            fontSize = 12.sp,
                            color = if (block.wholeApp) FocusTertiary else FocusOnSurfaceVariant
                        )
                    }
                    IconButton(onClick = { blocks.remove(block) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = FocusOnSurfaceVariant)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, FocusOutlineVariant, RoundedCornerShape(16.dp))
                        .clickable {
                            viewModel.loadAppsForPicker()
                            showAppPicker = true
                        }
                        .padding(14.dp)
                        .testTag("session_add_app"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = FocusPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Add an app", color = FocusPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            // ------------------------------------------------------- behaviour
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show the pause screen", fontSize = 15.sp, color = FocusOnSurface)
                        Text(
                            "Off: the blocked part just closes with a short message.",
                            fontSize = 11.sp,
                            color = FocusOnSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showPause,
                        onCheckedChange = { showPause = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = FocusSurface,
                            checkedTrackColor = FocusPrimary,
                            uncheckedTrackColor = FocusSurfaceContainerHighest
                        )
                    )
                }
            }

            if (existing != null) {
                item {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isLocked,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete this session", color = Color(0xFFFFB4AB)) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ----------------------------------------------------------------- dialogs
    if (showAppPicker) {
        val apps by viewModel.installedApps.collectAsStateWithLifecycle()
        val loading by viewModel.isLoadingInstalledApps.collectAsStateWithLifecycle()
        AppPickerDialog(
            apps = apps,
            isLoading = loading,
            onPick = { app ->
                showAppPicker = false
                if (ContentCatalog.targetsFor(app.packageName).isEmpty()) {
                    addOrMerge(ProfileBlock(app.packageName, app.label, wholeApp = true))
                } else {
                    choosingFor = app.packageName to app.label
                }
            },
            onDismiss = { showAppPicker = false }
        )
    }

    choosingFor?.let { (packageName, appName) ->
        BlockChoiceDialog(
            packageName = packageName,
            appName = appName,
            current = blocks.firstOrNull { it.packageName == packageName },
            onDone = { block ->
                addOrMerge(block)
                choosingFor = null
            },
            onDismiss = { choosingFor = null }
        )
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = FocusSurfaceContainer,
            title = { Text("Delete ${existing.name}?", color = FocusOnSurface) },
            text = { Text("This only removes the saved session. Your other blocks stay as they are.", color = FocusOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (viewModel.deleteSessionProfile(existing.id)) onBack()
                    else Toast.makeText(context, "End this session before deleting it", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = Color(0xFFFFB4AB)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = FocusOnSurfaceVariant) }
            }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = FocusSurfaceContainer,
            title = { Text("Save this session?", color = FocusOnSurface) },
            text = { Text("You have changes that aren't saved yet.", color = FocusOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    save()
                }) { Text("Save", color = FocusPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showLeaveConfirm = false }) { Text("Keep editing", color = FocusOnSurfaceVariant) }
                    TextButton(onClick = {
                        showLeaveConfirm = false
                        onBack()
                    }) { Text("Discard", color = Color(0xFFFFB4AB)) }
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = FocusOnSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun LockOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) FocusSurfaceContainerHigh else FocusSurfaceContainer)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) FocusPrimary else FocusOutlineVariant,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) FocusPrimary else FocusOnSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FocusOnSurface)
        Text(description, fontSize = 11.sp, color = FocusOnSurfaceVariant)
    }
}

/** Search and pick an installed app. */
@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    isLoading: Boolean,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusSurfaceContainer,
        title = { Text("Add an app", color = FocusOnSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (isLoading && apps.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FocusPrimary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(app) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(packageName = app.packageName, size = 30.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    fontSize = 14.sp,
                                    color = FocusOnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = FocusOnSurfaceVariant) }
        }
    )
}

/** Whole app, or only some parts of it (e.g. YouTube Shorts). */
@Composable
private fun BlockChoiceDialog(
    packageName: String,
    appName: String,
    current: ProfileBlock?,
    onDone: (ProfileBlock) -> Unit,
    onDismiss: () -> Unit
) {
    val targets = remember(packageName) { ContentCatalog.targetsFor(packageName) }
    var wholeApp by remember { mutableStateOf(current?.wholeApp ?: targets.isEmpty()) }
    val selected = remember {
        mutableStateListOf<String>().apply {
            addAll(
                current?.contentIds?.takeIf { it.isNotEmpty() }
                    ?: targets.filter { it.isShortForm }.map { it.id }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusSurfaceContainer,
        title = { Text("Block in $appName", color = FocusOnSurface) },
        text = {
            Column {
                ChoiceRow("Only some parts", "The rest of the app keeps working", !wholeApp) { wholeApp = false }
                if (!wholeApp) {
                    targets.forEach { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (target.id in selected) selected.remove(target.id) else selected.add(target.id)
                                }
                                .padding(start = 28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = target.id in selected,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(target.id) else selected.remove(target.id)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = FocusPrimary)
                            )
                            Column {
                                Text(target.label, fontSize = 14.sp, color = FocusOnSurface)
                                Text(target.description, fontSize = 11.sp, color = FocusOnSurfaceVariant)
                            }
                        }
                    }
                }
                ChoiceRow("Whole app", "$appName won't open at all", wholeApp) { wholeApp = true }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parts = selected.toList()
                    onDone(
                        ProfileBlock(
                            packageName = packageName,
                            appName = appName,
                            wholeApp = wholeApp || parts.isEmpty(),
                            contentIds = if (wholeApp) emptyList() else parts
                        )
                    )
                }
            ) { Text("Done", color = FocusPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FocusOnSurfaceVariant) }
        }
    )
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = FocusPrimary)
        )
        Column {
            Text(title, fontSize = 15.sp, color = FocusOnSurface)
            Text(subtitle, fontSize = 11.sp, color = FocusOnSurfaceVariant)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FocusOnSurface,
    unfocusedTextColor = FocusOnSurface,
    focusedBorderColor = FocusPrimary,
    unfocusedBorderColor = FocusSurfaceContainerHighest,
    cursorColor = FocusPrimary,
    focusedLabelColor = FocusPrimary,
    unfocusedLabelColor = FocusOnSurfaceVariant
)
