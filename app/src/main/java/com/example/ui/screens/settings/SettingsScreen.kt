package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.service.AccessibilityPermission
import com.example.service.PaymentPause
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionCard
import com.example.ui.components.rememberAccessibilityEnabled
import com.example.ui.intervention.InterventionActivity
import com.example.ui.theme.FocusError
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusOutline
import com.example.ui.theme.FocusOutlineVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusSurface
import com.example.ui.theme.FocusSurfaceContainerHighest
import com.example.viewmodel.FocusGuardViewModel

@Composable
fun SettingsScreen(
    viewModel: FocusGuardViewModel,
    onOpenPauseEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isStrict by viewModel.isStrictModeEnabled.collectAsStateWithLifecycle()
    val session = viewModel.activeSession.collectAsStateWithLifecycle()
    val isProtectionOn = rememberAccessibilityEnabled()
    var autoPause by remember { mutableStateOf(PaymentPause.isAutoPauseEnabled(context)) }
    // Only redraws when the lock changes, not on every second of a running session.
    val isLocked by remember {
        derivedStateOf { session.value?.let { it.isRunning && (it.hardLock || (isStrict && !it.isPaused)) } == true }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader(title = "Settings") }

        item {
            Section(title = "Blocking") {
                SettingRow(
                    title = "Accessibility access",
                    subtitle = if (isProtectionOn) {
                        "On. UltimateFocus can see which app is open."
                    } else {
                        "Off. Nothing can be blocked. Tap to switch it on."
                    },
                    trailingText = if (isProtectionOn) "On" else "Off",
                    trailingColor = if (isProtectionOn) FocusPrimary else FocusError,
                    onClick = { AccessibilityPermission.openServiceSwitch(context) },
                    tag = "accessibility_permission_row"
                )
                Divider()
                SettingSwitch(
                    title = "Pause for payment apps",
                    subtitle = "Google Pay, PhonePe and bank apps refuse to work while an " +
                        "accessibility app installed outside the Play Store is on. When you " +
                        "open one, the shield switches off and a notification helps you turn it back on.",
                    checked = autoPause,
                    onCheckedChange = {
                        autoPause = it
                        PaymentPause.setAutoPauseEnabled(context, it)
                    },
                    tag = "auto_payment_pause_switch"
                )
                if (isProtectionOn) {
                    Divider()
                    SettingRow(
                        title = "Pause the shield now",
                        subtitle = "For a payment app that wasn't recognised. Also available as a Quick Settings tile.",
                        onClick = { PaymentPause.requestPause(null) },
                        tag = "pause_now_row"
                    )
                }
            }
        }

        item {
            Section(title = "Focus sessions") {
                SettingSwitch(
                    title = "Strict Mode",
                    subtitle = when {
                        isLocked -> "Locked until this session ends."
                        isStrict -> "Sessions can't be paused or ended early, and blocks can't be changed during them."
                        else -> "Sessions can be paused or ended at any time."
                    },
                    checked = isStrict,
                    enabled = !isLocked,
                    onCheckedChange = { viewModel.setStrictMode(it) },
                    tag = "strict_mode_switch"
                )
            }
        }

        item {
            Section(title = "Pause screen") {
                SettingRow(
                    title = "Edit pause screen",
                    subtitle = "Background, photo, text and fonts",
                    onClick = onOpenPauseEditor,
                    tag = "edit_pause_screen_row"
                )
                Divider()
                SettingRow(
                    title = "Preview",
                    subtitle = "See it as it appears over a blocked app",
                    onClick = { context.startActivity(InterventionActivity.previewIntent(context)) },
                    tag = "preview_pause_screen_row"
                )
            }
        }

        item {
            Text(
                text = "UltimateFocus " + BuildConfig.VERSION_NAME,
                fontSize = 12.sp,
                color = FocusOutline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = FocusOnSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        SectionCard { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = FocusOutlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    trailingText: String? = null,
    trailingColor: Color = FocusOnSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FocusOnSurface)
            Text(text = subtitle, fontSize = 12.sp, color = FocusOnSurfaceVariant)
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = trailingColor,
                modifier = Modifier.padding(start = 8.dp)
            )
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
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FocusOnSurface)
            Text(text = subtitle, fontSize = 12.sp, color = FocusOnSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FocusSurface,
                checkedTrackColor = FocusPrimary,
                uncheckedTrackColor = FocusSurfaceContainerHighest
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
