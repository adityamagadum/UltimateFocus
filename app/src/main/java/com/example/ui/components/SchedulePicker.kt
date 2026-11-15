package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScheduleSpec
import com.example.ui.theme.FocusOnPrimary
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusSurfaceContainer
import com.example.ui.theme.FocusSurfaceContainerHigh
import com.example.ui.theme.FocusSurfaceContainerHighest

/** "When is this block on?" — all day or set hours, on every day, weekdays or weekends. */
@Composable
fun SchedulePicker(
    spec: ScheduleSpec,
    onChange: (ScheduleSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<TimeField?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "When is this block on?",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = FocusOnSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All day", spec.allDay) { onChange(spec.copy(allDay = true)) }
            Chip("Set hours", !spec.allDay) { onChange(spec.copy(allDay = false)) }
        }
        if (!spec.allDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("From " + ScheduleSpec.formatTime(spec.startMinute), false) {
                    editing = TimeField.START
                }
                Chip("To " + ScheduleSpec.formatTime(spec.endMinute), false) {
                    editing = TimeField.END
                }
            }
            if (spec.endMinute <= spec.startMinute) {
                Text(
                    text = "Runs past midnight, into the next day.",
                    fontSize = 11.sp,
                    color = FocusOnSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScheduleSpec.Days.entries.forEach { days ->
                Chip(days.label, spec.days == days) { onChange(spec.copy(days = days)) }
            }
        }
    }

    editing?.let { field ->
        TimePickerDialog(
            initialMinute = if (field == TimeField.START) spec.startMinute else spec.endMinute,
            onDismiss = { editing = null },
            onConfirm = { minuteOfDay ->
                onChange(
                    if (field == TimeField.START) spec.copy(startMinute = minuteOfDay)
                    else spec.copy(endMinute = minuteOfDay)
                )
                editing = null
            }
        )
    }
}

private enum class TimeField { START, END }

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) FocusPrimary else FocusSurfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) FocusOnPrimary else FocusOnSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusSurfaceContainerHigh,
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = FocusSurfaceContainer,
                    selectorColor = FocusPrimary,
                    timeSelectorSelectedContainerColor = FocusPrimary.copy(alpha = 0.25f),
                    periodSelectorSelectedContainerColor = FocusPrimary.copy(alpha = 0.25f)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("OK", color = FocusPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FocusOnSurfaceVariant)
            }
        }
    )
}
