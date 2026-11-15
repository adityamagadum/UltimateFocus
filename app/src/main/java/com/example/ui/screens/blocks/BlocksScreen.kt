package com.example.ui.screens.blocks

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.QuickBlock
import com.example.ui.components.AddBlockBottomSheet
import com.example.ui.components.AppBlockCard
import com.example.ui.components.DeleteRuleDialog
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionCard
import com.example.ui.theme.FocusOnPrimaryContainer
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusOutline
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusPrimaryContainer
import com.example.ui.theme.FocusSurface
import com.example.ui.theme.FocusSurfaceContainerHighest
import com.example.viewmodel.FocusGuardViewModel

@Composable
fun BlocksScreen(
    viewModel: FocusGuardViewModel,
    onMinimise: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val isAddSheetOpen by viewModel.isAddBlockSheetOpen.collectAsStateWithLifecycle()
    val ruleBeingEdited by viewModel.ruleBeingEdited.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingInstalledApps.collectAsStateWithLifecycle()
    val rulePendingDelete by viewModel.rulePendingDelete.collectAsStateWithLifecycle()
    val activeSession = viewModel.activeSession.collectAsStateWithLifecycle()
    val isStrict = viewModel.isStrictModeEnabled.collectAsStateWithLifecycle()
    // The session ticks every second; derivedStateOf means this screen only redraws when
    // the lock actually changes, not on every tick.
    val isLocked by remember {
        derivedStateOf {
            activeSession.value?.let { it.isRunning && (it.hardLock || (isStrict.value && !it.isPaused)) } == true
        }
    }
    val customRules = remember(rules) { rules.filterNot { QuickBlock.isQuickRule(it.id) } }
    val quickOn = remember(rules) {
        rules.filter { QuickBlock.isQuickRule(it.id) && it.isEnabled }.map { it.id }.toSet()
    }
    val alreadyBlocked = remember(customRules) { customRules.map { it.packageName }.toSet() }

    // Start loading the app list now, so the picker is ready when "Add" is tapped.
    LaunchedEffect(Unit) { viewModel.prefetchInstalledApps() }

    if (isAddSheetOpen) {
        AddBlockBottomSheet(
            onDismiss = { viewModel.setAddBlockSheetOpen(false) },
            onSaveBlock = { appName, packageName, mode, filterLabel, schedule,
                            allowanceMinutes, contentIds, contentBudget, showPauseScreen ->
                viewModel.saveRule(
                    appName, packageName, mode, filterLabel, schedule,
                    allowanceMinutes, contentIds, contentBudget, showPauseScreen
                )
            },
            installedApps = installedApps,
            isLoadingApps = isLoadingApps,
            // Quick blocks do not count: an app can have a quick block and a custom one.
            alreadyBlockedPackages = alreadyBlocked,
            onTeachScreen = { appName, packageName, mode, filterLabel, schedule, contentIds, contentBudget ->
                viewModel.teachScreen(
                    appName, packageName, mode, filterLabel, schedule, contentIds, contentBudget
                )
                // Leave the app so the user can open the one being taught.
                onMinimise()
            },
            existingRule = ruleBeingEdited
        )
    }

    rulePendingDelete?.let { pending ->
        DeleteRuleDialog(
            appName = pending.appName,
            onDismiss = { viewModel.cancelDeleteRule() },
            onConfirm = { viewModel.confirmDeleteRule() }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = "Blocks",
                subtitle = when (val active = rules.count { it.isEnabled }) {
                    0 -> "Nothing is blocked yet."
                    1 -> "1 active block"
                    else -> "$active active blocks"
                }
            )
        }

        if (isLocked) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = FocusPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Strict Mode: blocks are locked until your focus session ends.",
                        fontSize = 12.sp,
                        color = FocusOnSurfaceVariant
                    )
                }
            }
        }

        item {
            QuickBlocksSection(
                enabledRuleIds = quickOn,
                isLocked = isLocked,
                onToggle = { quick, on -> viewModel.setQuickBlock(quick, on) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Custom blocks",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FocusOnSurface
                )
                Text(
                    text = "Any app, whole or part of it, with your own schedule, time limit and pause screen.",
                    fontSize = 12.sp,
                    color = FocusOnSurfaceVariant
                )
            }
        }

        item {
            Button(
                onClick = { viewModel.startCreatingRule() },
                enabled = !isLocked,
                colors = ButtonDefaults.buttonColors(containerColor = FocusPrimaryContainer),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp)
                    .testTag("add_block_rule_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = FocusOnPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add a custom block",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = FocusOnPrimaryContainer
                )
            }
        }

        if (customRules.isEmpty()) {
            item {
                SectionCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Three ways to block an app",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FocusOnSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    listOf(
                        "Part of an app: stop Shorts, Reels or Stories and keep the rest working.",
                        "Whole app: the app doesn't open at all.",
                        "Daily limit: use the app for a set time each day, then it's blocked."
                    ).forEach {
                        Text(
                            text = "• $it",
                            fontSize = 13.sp,
                            color = FocusOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        } else {
            items(customRules, key = { it.id }) { rule ->
                AppBlockCard(
                    rule = rule,
                    isLocked = isLocked,
                    onToggle = { viewModel.toggleRule(rule.id) },
                    onEditClick = { viewModel.startEditingRule(rule.id) },
                    onDeleteClick = { viewModel.requestDeleteRule(rule.id) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
