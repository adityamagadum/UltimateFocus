package com.example.data.model

/**
 * One-switch blocks for the feeds most people want gone, such as YouTube Shorts and
 * Instagram Reels.
 *
 * Each is stored as an ordinary rule with a fixed id, so the blocking engine, the
 * statistics and Strict Mode treat it like any other. It is always on, all day, and uses
 * a plain block: the app is backed out of the feed without the pause screen.
 */
data class QuickBlock(
    val contentId: String,
    val title: String,
    val appName: String,
    val packageName: String
) {
    val ruleId: String get() = RULE_PREFIX + contentId

    fun toRule(): BlockedAppRule = BlockedAppRule(
        id = ruleId,
        appName = appName,
        packageName = packageName,
        blockMode = BlockMode.CONTENT_LEVEL,
        filterLabel = title,
        scheduleText = ScheduleSpec.ALWAYS.toText(),
        isEnabled = true,
        blockedContentIds = listOf(contentId),
        showPauseScreen = false
    )

    companion object {
        const val RULE_PREFIX = "quick_"

        val ALL: List<QuickBlock> = listOf(
            QuickBlock("youtube_shorts", "YouTube Shorts", "YouTube", ContentCatalog.YOUTUBE),
            QuickBlock("instagram_reels", "Instagram Reels", "Instagram", ContentCatalog.INSTAGRAM),
            QuickBlock("facebook_reels", "Facebook Reels", "Facebook", ContentCatalog.FACEBOOK),
            QuickBlock("snapchat_spotlight", "Snapchat Spotlight", "Snapchat", ContentCatalog.SNAPCHAT)
        )

        fun isQuickRule(ruleId: String): Boolean = ruleId.startsWith(RULE_PREFIX)
    }
}
