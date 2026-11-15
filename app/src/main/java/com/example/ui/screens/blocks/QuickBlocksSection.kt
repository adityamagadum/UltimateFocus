package com.example.ui.screens.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuickBlock
import com.example.ui.components.AppIcon
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusOutline
import com.example.ui.theme.FocusOutlineVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusSurface
import com.example.ui.theme.FocusSurfaceContainer
import com.example.ui.theme.FocusSurfaceContainerHighest

/**
 * One switch per feed. On means that feed is blocked all day, every day, with a plain
 * block: the app is simply backed out of it. No setup, no pause screen.
 */
@Composable
fun QuickBlocksSection(
    enabledRuleIds: Set<String>,
    isLocked: Boolean,
    onToggle: (QuickBlock, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installed = remember {
        QuickBlock.ALL.associate { quick ->
            quick.packageName to try {
                context.packageManager.getApplicationInfo(quick.packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    // Apps on the phone first; the rest stay listed so the choice is clear.
    val ordered = QuickBlock.ALL.sortedByDescending { installed[it.packageName] == true }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Quick blocks",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = FocusOnSurface
        )
        Text(
            text = "Switch on to block it all day. You're sent back out of it, and the rest of the app keeps working.",
            fontSize = 12.sp,
            color = FocusOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        ordered.forEach { quick ->
            val isOn = quick.ruleId in enabledRuleIds
            val isInstalled = installed[quick.packageName] == true
            // Strict Mode never stops a block being switched on, only off.
            val canChange = isInstalled && !(isLocked && isOn)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(FocusSurfaceContainer)
                    .border(
                        width = 1.dp,
                        color = if (isOn) FocusPrimary.copy(alpha = 0.6f) else FocusOutlineVariant,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable(enabled = canChange) { onToggle(quick, !isOn) }
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .testTag("quick_block_" + quick.contentId),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = quick.packageName, size = 40.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quick.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isInstalled) FocusOnSurface else FocusOutline
                    )
                    Text(
                        text = when {
                            !isInstalled -> quick.appName + " isn't installed"
                            isOn -> "Blocked"
                            else -> "Allowed"
                        },
                        fontSize = 12.sp,
                        color = if (isOn) FocusPrimary else FocusOnSurfaceVariant
                    )
                }
                Switch(
                    checked = isOn,
                    onCheckedChange = { onToggle(quick, it) },
                    enabled = canChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FocusSurface,
                        checkedTrackColor = FocusPrimary,
                        uncheckedThumbColor = FocusOutline,
                        uncheckedTrackColor = FocusSurfaceContainerHighest
                    )
                )
            }
        }
    }
}
