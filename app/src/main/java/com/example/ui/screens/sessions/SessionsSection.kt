package com.example.ui.screens.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SessionLock
import com.example.data.model.SessionProfile
import com.example.ui.theme.FocusOnPrimaryContainer
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusOutlineVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusPrimaryContainer
import com.example.ui.theme.FocusSurfaceContainer
import com.example.ui.theme.FocusSurfaceContainerHigh
import com.example.ui.theme.FocusSurfaceContainerHighest
import com.example.ui.theme.FocusTertiary

/** Home's "Your sessions": saved custom sessions, one tap to start. */
@Composable
fun SessionsSection(
    profiles: List<SessionProfile>,
    onNew: () -> Unit,
    onPick: (SessionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Your sessions",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = FocusOnSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onNew, modifier = Modifier.testTag("new_session_profile")) {
                Icon(Icons.Default.Add, contentDescription = null, tint = FocusPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New", color = FocusPrimary)
            }
        }

        if (profiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FocusSurfaceContainer)
                    .clickable(onClick = onNew)
                    .padding(16.dp)
            ) {
                Text("🧒  Create a session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FocusOnSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "For example a Kid session: YouTube works but Shorts don't, and Instagram " +
                        "won't open. Save it once, start it any time.",
                    fontSize = 12.sp,
                    color = FocusOnSurfaceVariant
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    Column(
                        modifier = Modifier
                            .width(156.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(FocusSurfaceContainerHigh)
                            .clickable { onPick(profile) }
                            .padding(14.dp)
                            .testTag("session_profile_" + profile.id)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.emoji, fontSize = 26.sp, modifier = Modifier.weight(1f))
                            LockBadge(profile.lock)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            profile.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FocusOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            profile.summary,
                            fontSize = 11.sp,
                            color = FocusOnSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockBadge(lock: SessionLock) {
    val hard = lock == SessionLock.HARD
    Icon(
        imageVector = if (hard) Icons.Default.Lock else Icons.Default.LockOpen,
        contentDescription = if (hard) "Hard lock" else "Soft lock",
        tint = if (hard) FocusTertiary else FocusOnSurfaceVariant,
        modifier = Modifier.size(16.dp)
    )
}

/** Picks a length and starts a saved session, or opens it for editing. */
@Composable
fun StartProfileDialog(
    profile: SessionProfile,
    onStart: (minutes: Int) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var minutes by remember(profile.id) { mutableIntStateOf(profile.defaultMinutes) }
    val hard = profile.lock == SessionLock.HARD

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusSurfaceContainer,
        title = {
            Text((profile.emoji + "  " + profile.name).trim(), color = FocusOnSurface)
        },
        text = {
            Column {
                Text(profile.summary, fontSize = 13.sp, color = FocusOnSurfaceVariant)
                if (profile.blocks.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    profile.blocks.take(4).forEach { block ->
                        Text(
                            "• " + block.appName + " — " + block.label,
                            fontSize = 12.sp,
                            color = FocusOnSurfaceVariant
                        )
                    }
                    if (profile.blocks.size > 4) {
                        Text("• and ${profile.blocks.size - 4} more", fontSize = 12.sp, color = FocusOnSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("How long?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FocusOnSurface)
                Spacer(Modifier.height(8.dp))
                DurationChips(selected = minutes, onSelect = { minutes = it })
                if (hard) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FocusTertiary.copy(alpha = 0.12f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = FocusTertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Hard lock: once started, it can't be paused or stopped for " +
                                formatMinutes(minutes) + ".",
                            fontSize = 12.sp,
                            color = FocusOnSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(minutes) },
                modifier = Modifier.testTag("start_profile_session")
            ) { Text("Start", color = FocusPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Edit", color = FocusOnSurfaceVariant) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = FocusOnSurfaceVariant) }
            }
        }
    )
}

/** Session length chips, shared by the start dialog and the editor. */
@Composable
fun DurationChips(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(SessionProfile.DURATIONS_MINUTES) { minutes ->
            val isSelected = minutes == selected
            Text(
                text = formatMinutes(minutes),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) FocusOnPrimaryContainer else FocusOnSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) FocusPrimaryContainer else FocusSurfaceContainerHighest)
                    .border(1.dp, if (isSelected) Color.Transparent else FocusOutlineVariant, RoundedCornerShape(16.dp))
                    .clickable { onSelect(minutes) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

internal fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
