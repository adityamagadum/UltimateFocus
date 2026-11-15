package com.example.ui.screens.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSession
import com.example.ui.components.FocusRing
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionCard
import com.example.ui.theme.FocusErrorContainer
import com.example.ui.theme.FocusOnErrorContainer
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusSurfaceContainerHighest
import com.example.viewmodel.FocusGuardViewModel

/** The running focus session: time left, what is blocked, and the controls. */
@Composable
fun FocusSessionScreen(
    viewModel: FocusGuardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session by viewModel.activeSession.collectAsStateWithLifecycle()
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val isStrict by viewModel.isStrictModeEnabled.collectAsStateWithLifecycle()

    // Set when the user taps End: the screen keeps showing the session it is closing
    // while it slides away, instead of flashing "No session running" on the way out.
    var closingSnapshot by remember { mutableStateOf<FocusSession?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val current = closingSnapshot ?: session
        // A custom session shows its own name, e.g. "🧒 Kid session".
        val headerTitle = current?.takeIf { it.profileId != null }
            ?.let { (it.emoji + " " + it.title).trim() } ?: "Focus session"
        ScreenHeader(title = headerTitle, onBack = onBack)

        if (current == null || !current.isRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No session running. Start one from Home.",
                    fontSize = 16.sp,
                    color = FocusOnSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Back to Home")
                }
            }
            return@Column
        }

        val locked = current.hardLock || (isStrict && !current.isPaused)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            FocusRing(
                progress = current.progressFraction,
                scoreLabel = if (current.isPaused) "Paused" else "Time left",
                timeText = current.formattedRemainingTime,
                subLabel = if (current.isPaused) "Resume when you're ready" else "Ends " + current.endTimeText,
                size = 230.dp
            )

            if (dailyStats.todayGoalText.isNotBlank()) {
                Text(
                    text = "“" + dailyStats.todayGoalText + "”",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = FocusOnSurface,
                    textAlign = TextAlign.Center
                )
            }

            SectionCard {
                Text(
                    text = "Blocked during this session",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FocusOnSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (current.blockedApps.isEmpty()) {
                    Text(
                        text = "Nothing. Add blocks on the Blocks tab and they apply to the next session.",
                        fontSize = 12.sp,
                        color = FocusOnSurfaceVariant
                    )
                } else {
                    current.blockedApps.forEach { line ->
                        Text(
                            text = "• " + line,
                            fontSize = 13.sp,
                            color = FocusOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = FocusPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (current.hardLock) {
                            "Hard lock: this session can't be paused or ended until the time is up."
                        } else {
                            "Strict Mode: this session can't be paused or ended early."
                        },
                        fontSize = 12.sp,
                        color = FocusOnSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.togglePauseSession() },
                    enabled = !locked,
                    colors = ButtonDefaults.buttonColors(containerColor = FocusSurfaceContainerHighest),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("pause_session_button")
                ) {
                    Icon(
                        imageVector = if (current.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = FocusOnSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (current.isPaused) "Resume" else "Pause", color = FocusOnSurface)
                }
                Button(
                    onClick = {
                        if (closingSnapshot == null) {
                            val snapshot = current
                            if (viewModel.endSession()) {
                                closingSnapshot = snapshot
                                onBack()
                            }
                        }
                    },
                    enabled = !locked,
                    colors = ButtonDefaults.buttonColors(containerColor = FocusErrorContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("end_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = FocusOnErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("End session", color = FocusOnErrorContainer)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
