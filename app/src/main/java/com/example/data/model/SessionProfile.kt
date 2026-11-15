package com.example.data.model

import com.squareup.moshi.JsonClass

/**
 * How firmly a custom session holds.
 *
 * SOFT can be paused or ended at any time. HARD cannot be paused or ended, and blocks
 * and sessions cannot be edited, until its timer runs out — for handing the phone to a
 * child, or for holding yourself to a plan.
 */
enum class SessionLock { SOFT, HARD }

/**
 * A saved, reusable session such as "Kid session": its own list of what is blocked,
 * applied on top of the user's normal rules only while that session runs.
 */
@JsonClass(generateAdapter = true)
data class SessionProfile(
    val id: String,
    val name: String,
    val emoji: String = "🧒",
    val lock: SessionLock = SessionLock.SOFT,
    val blocks: List<ProfileBlock> = emptyList(),
    /** True shows the pause screen when something is blocked; false just closes it. */
    val showPauseScreen: Boolean = true,
    /** Length offered first when starting this session. */
    val defaultMinutes: Int = 60
) {
    /** One line for cards, e.g. "Blocks 2 apps · Hard lock". */
    val summary: String
        get() {
            val count = blocks.size
            val what = when (count) {
                0 -> "Nothing blocked yet"
                1 -> "Blocks 1 app"
                else -> "Blocks $count apps"
            }
            val lockLabel = if (lock == SessionLock.HARD) "Hard lock" else "Soft lock"
            return "$what · $lockLabel"
        }

    /**
     * This session's blocks as ordinary rules, so the blocking engine, the detectors,
     * the pause screen and the statistics handle them exactly like any other rule.
     * They are on all day; the session itself decides when they apply.
     */
    fun toRules(): List<BlockedAppRule> = blocks.map { block ->
        BlockedAppRule(
            id = RULE_PREFIX + id + "_" + block.packageName,
            appName = block.appName,
            packageName = block.packageName,
            blockMode = if (block.wholeApp) BlockMode.HARD_BLOCK else BlockMode.CONTENT_LEVEL,
            filterLabel = block.label,
            scheduleText = ScheduleSpec.ALWAYS.toText(),
            isEnabled = true,
            blockedContentIds = if (block.wholeApp) emptyList() else block.contentIds,
            showPauseScreen = showPauseScreen
        )
    }

    companion object {
        const val RULE_PREFIX = "session_rule_"
        val EMOJIS = listOf("🧒", "📚", "💼", "🌙", "🎮", "🧘", "🏃", "🍽️", "🚗", "🎯")
        val DURATIONS_MINUTES = listOf(15, 30, 60, 90, 120, 180, 240)
    }
}

/** One app in a session: the whole app, or only some parts of it (e.g. Shorts). */
@JsonClass(generateAdapter = true)
data class ProfileBlock(
    val packageName: String,
    val appName: String,
    val wholeApp: Boolean,
    /** Ids from [ContentCatalog]; used when [wholeApp] is false. */
    val contentIds: List<String> = emptyList()
) {
    val label: String
        get() = if (wholeApp) {
            "Whole app"
        } else {
            contentIds.mapNotNull { ContentCatalog.byId(it)?.label }
                .ifEmpty { listOf("Short videos") }
                .joinToString(", ")
        }
}
