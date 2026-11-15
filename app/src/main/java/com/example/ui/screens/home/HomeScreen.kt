package com.example.ui.screens.home

import com.example.ui.screens.sessions.StartProfileDialog
import com.example.ui.screens.sessions.SessionsSection
import com.example.data.model.SessionProfile
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.analytics.FocusAnalytics
import com.example.data.model.FocusSession
import com.example.service.AccessibilityPermission
import com.example.ui.components.EditGoalDialog
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionCard
import com.example.ui.components.StatTile
import com.example.ui.components.rememberAccessibilityEnabled
import com.example.ui.theme.FocusError
import com.example.ui.theme.FocusErrorContainer
import com.example.ui.theme.FocusOnErrorContainer
import com.example.ui.theme.FocusOnPrimary
import com.example.ui.theme.FocusOnPrimaryContainer
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusPrimaryContainer
import com.example.ui.theme.FocusSurfaceContainerHigh
import com.example.viewmodel.FocusGuardViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: FocusGuardViewModel,
    onOpenSession: () -> Unit,
    onOpenBlocks: () -> Unit,
    /** Opens the custom-session editor; null creates a new session. */
    onEditSessionProfile: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val selectedDuration by viewModel.selectedDurationMinutes.collectAsStateWithLifecycle()
    val isEditGoalOpen by viewModel.isEditGoalDialogOpen.collectAsStateWithLifecycle()
    val isProtectionOn = rememberAccessibilityEnabled()
    val activeRules = remember(rules) { rules.count { it.isEnabled } }
    // Worked out once per visit rather than on every redraw.
    val greeting = remember { greetingForNow() }
    val today = remember { todayLabel() }

    // The saved session the user tapped, waiting for a length to be chosen.
    var startingProfile by remember { mutableStateOf<SessionProfile?>(null) }
    startingProfile?.let { profile ->
        StartProfileDialog(
            profile = profile,
            onStart = { minutes ->
                startingProfile = null
                if (viewModel.startProfileSession(profile.id, minutes)) {
                    onOpenSession()
                } else {
                    Toast.makeText(context, "A session is already running", Toast.LENGTH_SHORT).show()
                }
            },
            onEdit = {
                startingProfile = null
                onEditSessionProfile(profile.id)
            },
            onDismiss = { startingProfile = null }
        )
    }

    if (isEditGoalOpen) {
        EditGoalDialog(
            initialGoal = dailyStats.todayGoalText,
            onDismiss = { viewModel.setEditGoalDialogOpen(false) },
            onSave = { viewModel.updateGoal(it) }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(title = greeting, subtitle = today)
        }

        item {
            ProtectionStatus(
                isProtectionOn = isProtectionOn,
                activeRules = activeRules,
                onTurnOn = { AccessibilityPermission.openServiceSwitch(context) },
                onOpenBlocks = onOpenBlocks,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        item {
            // Read here, inside the one card that shows it, so the per-second countdown
            // redraws only this card instead of the whole Home screen.
            val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
            val session = activeSession
            if (session != null && session.isRunning) {
                RunningSessionCard(
                    session = session,
                    onOpen = onOpenSession,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else {
                StartSessionCard(
                    selectedDuration = selectedDuration,
                    activeRules = activeRules,
                    onSelect = { viewModel.selectDuration(it) },
                    onStart = {
                        viewModel.startFocusSession()
                        onOpenSession()
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        item {
            val profiles by viewModel.sessionProfiles.collectAsStateWithLifecycle()
            SessionsSection(
                profiles = profiles,
                onNew = { onEditSessionProfile(null) },
                onPick = { profile ->
                    // One session at a time: a running one has to finish first.
                    if (viewModel.activeSession.value?.isRunning == true) {
                        Toast.makeText(context, "End the current session first", Toast.LENGTH_SHORT).show()
                    } else {
                        startingProfile = profile
                    }
                }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    value = FocusAnalytics.formatMinutes(dailyStats.focusedTodayMinutes),
                    label = "Focused today",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = dailyStats.blockedAttemptsToday.toString(),
                    label = "Blocked today",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = dailyStats.activeScreenTimeText,
                    label = "Screen time",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            GoalCard(
                goal = dailyStats.todayGoalText,
                onEdit = { viewModel.setEditGoalDialogOpen(true) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProtectionStatus(
    isProtectionOn: Boolean,
    activeRules: Int,
    onTurnOn: () -> Unit,
    onOpenBlocks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, detail) = when {
        !isProtectionOn -> "Blocking is off" to "Tap to switch UltimateFocus on in Accessibility settings."
        activeRules == 0 -> "No blocks yet" to "Tap to choose what to block."
        activeRules == 1 -> "Protection on" to "1 block is active."
        else -> "Protection on" to "$activeRules blocks are active."
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isProtectionOn) FocusSurfaceContainerHigh else FocusErrorContainer.copy(alpha = 0.35f))
            .clickable { if (isProtectionOn) onOpenBlocks() else onTurnOn() }
            .padding(14.dp)
            .testTag("protection_status"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isProtectionOn) Icons.Default.Shield else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (isProtectionOn) FocusPrimary else FocusError,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isProtectionOn) FocusOnSurface else FocusOnErrorContainer
            )
            Text(text = detail, fontSize = 12.sp, color = FocusOnSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = FocusOnSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StartSessionCard(
    selectedDuration: Int,
    activeRules: Int,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Text(
            text = "Focus session",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = FocusOnSurface
        )
        Text(
            text = if (activeRules > 0) {
                "All your blocks stay on for the whole session, whatever their schedule."
            } else {
                "Add a block first, so the session has something to keep you away from."
            },
            fontSize = 12.sp,
            color = FocusOnSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SESSION_LENGTHS) { minutes ->
                val isSelected = selectedDuration == minutes
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) FocusPrimary else FocusSurfaceContainerHigh)
                        .clickable { onSelect(minutes) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("duration_chip_$minutes")
                ) {
                    Text(
                        text = FocusAnalytics.formatMinutes(minutes),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) FocusOnPrimary else FocusOnSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = FocusPrimaryContainer),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_focus_button")
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = FocusOnPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start " + FocusAnalytics.formatMinutes(selectedDuration) + " session",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = FocusOnPrimaryContainer
            )
        }
    }
}

@Composable
private fun RunningSessionCard(
    session: FocusSession,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FocusSurfaceContainerHigh)
            .clickable(onClick = onOpen)
            .padding(16.dp)
            .testTag("active_session_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (session.isPaused) FocusOnSurfaceVariant else FocusPrimary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    session.isPaused -> "Session paused"
                    session.profileId != null ->
                        (session.emoji + " " + session.title).trim() +
                            if (session.hardLock) " · Hard lock" else " · running"
                    else -> "Focus session running"
                },
                fontSize = 13.sp,
                color = FocusOnSurfaceVariant
            )
            Text(
                text = session.formattedRemainingTime,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = FocusOnSurface
            )
            Text(
                text = "Ends " + session.endTimeText,
                fontSize = 12.sp,
                color = FocusOnSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open session",
            tint = FocusOnSurfaceVariant
        )
    }
}

@Composable
private fun GoalCard(goal: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = FocusPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "YOUR GOAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FocusOnSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("edit_goal_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit goal",
                    tint = FocusOnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = goal.ifBlank { "Set a goal. It is shown on the pause screen whenever a block stops you." },
            fontSize = if (goal.isBlank()) 13.sp else 16.sp,
            fontWeight = if (goal.isBlank()) FontWeight.Normal else FontWeight.Medium,
            color = if (goal.isBlank()) FocusOnSurfaceVariant else FocusOnSurface
        )
    }
}

private val SESSION_LENGTHS = listOf(15, 25, 45, 60, 90, 120)

private fun todayLabel(): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}
