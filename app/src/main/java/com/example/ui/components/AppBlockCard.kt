package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockMode
import com.example.data.model.BlockedAppRule
import com.example.ui.theme.FocusError
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusOutline
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusSecondary
import com.example.ui.theme.FocusSurface
import com.example.ui.theme.FocusSurfaceContainer
import com.example.ui.theme.FocusSurfaceContainerHighest
import com.example.ui.theme.FocusTertiary

/** One block rule: what it blocks, when, today's usage for limits, and its controls. */
@Composable
fun AppBlockCard(
    rule: BlockedAppRule,
    isLocked: Boolean,
    onToggle: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FocusSurfaceContainer)
            .padding(14.dp)
            .testTag("app_block_card_${rule.packageName}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = rule.packageName, size = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FocusOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.filterLabel,
                    fontSize = 12.sp,
                    color = modeColor(rule.blockMode),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { onToggle() },
                enabled = !isLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FocusSurface,
                    checkedTrackColor = FocusPrimary,
                    uncheckedThumbColor = FocusOutline,
                    uncheckedTrackColor = FocusSurfaceContainerHighest
                ),
                modifier = Modifier.testTag("switch_${rule.packageName}")
            )
        }

        if (rule.blockMode == BlockMode.DAILY_ALLOWANCE) {
            val left = (rule.totalAllowedMinutes - rule.usedMinutes).coerceAtLeast(0)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${rule.usedMinutes} of ${rule.totalAllowedMinutes} min used today",
                    fontSize = 12.sp,
                    color = FocusOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (left == 0) "Limit reached" else "$left min left",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (left == 0) FocusError else FocusTertiary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (rule.totalAllowedMinutes <= 0) 0f
                    else (rule.usedMinutes.toFloat() / rule.totalAllowedMinutes).coerceIn(0f, 1f)
                },
                color = FocusTertiary,
                trackColor = FocusSurfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = FocusOutline,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = rule.scheduleText,
                fontSize = 12.sp,
                color = FocusOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = rule.attemptsBlockedCount.toString() + " blocked",
                fontSize = 12.sp,
                color = FocusOnSurfaceVariant
            )
        }

        if (!isLocked) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardAction(text = "Edit", color = FocusOnSurface, tag = "edit_rule_${rule.packageName}", onClick = onEditClick)
                CardAction(text = "Delete", color = FocusError, tag = "delete_rule_${rule.packageName}", onClick = onDeleteClick)
            }
        }
    }
}

@Composable
private fun CardAction(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    tag: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FocusSurfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(tag)
    )
}

private fun modeColor(mode: BlockMode) = when (mode) {
    BlockMode.CONTENT_LEVEL -> FocusSecondary
    BlockMode.HARD_BLOCK -> FocusError
    BlockMode.DAILY_ALLOWANCE -> FocusTertiary
}
