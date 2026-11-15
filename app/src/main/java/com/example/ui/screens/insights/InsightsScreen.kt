package com.example.ui.screens.insights

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Timeframe
import com.example.ui.components.AppIcon
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionCard
import com.example.ui.components.StatTile
import com.example.ui.components.WeeklyBalanceChart
import com.example.ui.theme.FocusOnPrimary
import com.example.ui.theme.FocusOnSurface
import com.example.ui.theme.FocusOnSurfaceVariant
import com.example.ui.theme.FocusPrimary
import com.example.ui.theme.FocusSurfaceContainer
import com.example.ui.theme.FocusSurfaceContainerHighest
import com.example.ui.theme.FocusTertiary
import com.example.viewmodel.FocusGuardViewModel

@Composable
fun InsightsScreen(
    viewModel: FocusGuardViewModel,
    modifier: Modifier = Modifier
) {
    val insights by viewModel.insightsStats.collectAsStateWithLifecycle()
    val selected by viewModel.selectedTimeframe.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader(title = "Insights", subtitle = "Measured on this phone") }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FocusSurfaceContainer)
                    .padding(4.dp)
            ) {
                Timeframe.entries.forEach { timeframe ->
                    val isSelected = timeframe == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) FocusPrimary else FocusSurfaceContainer)
                            .clickable { viewModel.selectTimeframe(timeframe) }
                            .padding(vertical = 9.dp)
                            .testTag("timeframe_" + timeframe.name),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeframe.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) FocusOnPrimary else FocusOnSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "SCREEN TIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = FocusPrimary
                )
                Text(
                    text = insights.screenTimeText,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = FocusOnSurface
                )
                Text(
                    text = insights.screenTimeComparisonText,
                    fontSize = 13.sp,
                    color = FocusOnSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(insights.deepFocusText, "Focus time", Modifier.weight(1f))
                StatTile(insights.blockedCount.toString(), "Blocked", Modifier.weight(1f))
                StatTile(insights.recoveredText, "Saved (est.)", Modifier.weight(1f))
            }
        }

        item {
            WeeklyBalanceChart(
                days = insights.weeklyBalance,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        item {
            SectionCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Most blocked apps",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FocusOnSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (insights.appInterventions.isEmpty()) {
                    Text(
                        text = "Nothing blocked in this period.",
                        fontSize = 13.sp,
                        color = FocusOnSurfaceVariant
                    )
                }
                insights.appInterventions.take(MAX_APPS).forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(packageName = app.packageName, size = 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row {
                                Text(
                                    text = app.appName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FocusOnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = app.attemptsCount.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FocusOnSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { app.progressFraction },
                                color = FocusTertiary,
                                trackColor = FocusSurfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = FocusTertiary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (insights.streakDays) {
                                0 -> "No focus streak yet"
                                1 -> "1-day focus streak"
                                else -> insights.streakDays.toString() + "-day focus streak"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FocusOnSurface
                        )
                        Text(
                            text = "Days in a row with at least one focus session. Completed sessions on " +
                                insights.protectedPercent + "% of days in this period.",
                            fontSize = 12.sp,
                            color = FocusOnSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private const val MAX_APPS = 6
