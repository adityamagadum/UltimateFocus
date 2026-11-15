package com.example.data.model

import com.squareup.moshi.JsonClass

enum class BlockMode {
    CONTENT_LEVEL,
    HARD_BLOCK,
    DAILY_ALLOWANCE
}

@JsonClass(generateAdapter = true)
data class BlockedAppRule(
    val id: String,
    val appName: String,
    val packageName: String,
    val blockMode: BlockMode,
    val filterLabel: String,
    val scheduleText: String,
    val isEnabled: Boolean = true,
    val attemptsBlockedCount: Int = 0,
    val usedMinutes: Int = 0,
    val totalAllowedMinutes: Int = 0,
    /**
     * Ids from [ContentCatalog] this rule blocks, for CONTENT_LEVEL rules.
     * Empty means "the app's short-form feeds", which is how older rules behave.
     */
    val blockedContentIds: List<String> = emptyList(),
    /**
     * Daily budget in minutes for the selected content, for CONTENT_LEVEL rules.
     * Zero means block on sight, which is the original behaviour.
     */
    val contentAllowanceMinutes: Int = 0,
    /**
     * View ids captured from the user's own device with "Teach this screen".
     *
     * Built-in catalogue entries are written from published naming and cannot be
     * verified for every app and version; these are observed on the actual phone, so
     * they are the authoritative signal when present.
     */
    val learnedViewIds: List<String> = emptyList(),
    /**
     * True shows the full pause screen when this rule blocks. False is a plain block:
     * the app is backed out of the blocked part with a short message and nothing else.
     */
    val showPauseScreen: Boolean = true
)

/** The running focus session as the UI sees it; derived every second from the clock. */
data class FocusSession(
    val id: String,
    val title: String = "Focus session",
    val targetGoal: String = "",
    val remainingSeconds: Int,
    val totalDurationSeconds: Int,
    val isRunning: Boolean = true,
    val isPaused: Boolean = false,
    val blockedApps: List<String> = emptyList(),
    val endTimeText: String = "",
    /** Set when a custom session (e.g. "Kid session") is running. */
    val profileId: String? = null,
    val emoji: String = "",
    /** A hard-locked custom session cannot be paused, ended or loosened until it ends. */
    val hardLock: Boolean = false
) {
    val formattedRemainingTime: String
        get() {
            val hours = remainingSeconds / 3600
            val minutes = (remainingSeconds % 3600) / 60
            val seconds = remainingSeconds % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%02d:%02d".format(minutes, seconds)
        }

    val progressFraction: Float
        get() = if (totalDurationSeconds > 0) {
            1f - (remainingSeconds.toFloat() / totalDurationSeconds)
        } else 0f
}

/**
 * What the pause screen shows when a blocked app is opened.
 *
 * [anchorImageUri] points at a copy of the user's chosen photo inside the app's own
 * storage; empty means no photo.
 */
@JsonClass(generateAdapter = true)
data class InterventionConfig(
    val customQuote: String = "Is this what you meant to do right now?",
    val subMessage: String = "",
    val anchorImageUri: String = "",
    /**
     * The designed pause screen (background, text anywhere, fonts). Null on installs
     * that have not opened the new editor yet; [PauseDesign.fromLegacy] then turns the
     * fields above into a design so the old message and photo still show.
     */
    val design: PauseDesign? = null
) {
    /** The design to show: the saved one, or one built from the older settings. */
    fun resolvedDesign(): PauseDesign = design ?: PauseDesign.fromLegacy(this)
}

data class AppInterventionStat(
    val appName: String,
    val packageName: String,
    val attemptsCount: Int,
    val progressFraction: Float = 0f
)

data class WeeklyBalanceDay(
    val dayLabel: String,
    val focusFraction: Float,
    val impulseFraction: Float,
    val isHighlighted: Boolean = false
)

/** Today's figures for Home. Every value is measured; a fresh install shows zeros. */
data class DailyStats(
    val focusScorePercent: Int = 0,
    val focusedTodayMinutes: Int = 0,
    val recoveredMinutesToday: Int = 0,
    val blockedAttemptsToday: Int = 0,
    val totalBlockedAllTime: Int = 0,
    val activeScreenTimeText: String = "0m",
    val streakDays: Int = 0,
    val todayGoalText: String = ""
)
