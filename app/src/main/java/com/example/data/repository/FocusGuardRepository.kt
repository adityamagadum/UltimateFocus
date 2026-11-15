package com.example.data.repository

import com.example.data.local.FocusGuardPreferences
import com.example.data.local.PersistedSession
import com.example.data.model.AppInterventionStat
import com.example.data.model.BlockMode
import com.example.data.model.BlockedAppRule
import com.example.data.model.DailyStats
import com.example.data.model.DailyUsageSnapshot
import com.example.data.analytics.FocusAnalytics
import com.example.data.model.DistractionAttempt
import com.example.data.model.FocusSessionRecord
import com.example.data.model.FocusSession
import com.example.data.model.InterventionConfig
import com.example.data.model.SessionLock
import com.example.data.model.SessionProfile
import com.example.data.model.WeeklyBalanceDay
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for FocusGuard state.
 *
 * State is held in memory as [MutableStateFlow] so that synchronous `.value` reads stay
 * available (the blocking engine needs them from an AccessibilityService callback), and
 * every mutation is written through to [FocusGuardPreferences]. On construction the
 * in-memory state is hydrated from disk; until that completes the seeded defaults are
 * shown, which is visually identical to the persisted first-run values.
 */
class FocusGuardRepository(
    private val preferences: FocusGuardPreferences,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val _rules = MutableStateFlow<List<BlockedAppRule>>(emptyList())
    val rules: StateFlow<List<BlockedAppRule>> = _rules.asStateFlow()

    private val _shortsAndReelsMasterShield = MutableStateFlow(true)
    val shortsAndReelsMasterShield: StateFlow<Boolean> = _shortsAndReelsMasterShield.asStateFlow()

    /**
     * When on, a running focus session cannot be paused, ended early or loosened: rules
     * cannot be edited and Strict Mode itself cannot be switched off until it ends.
     * Off by default, so nobody is locked in before they have chosen to be.
     */
    private val _isStrictModeEnabled = MutableStateFlow(false)
    val isStrictModeEnabled: StateFlow<Boolean> = _isStrictModeEnabled.asStateFlow()

    /**
     * Wall-clock backed session. Null when no session is configured. The UI-facing
     * [FocusSession] is recomputed from this on every tick.
     */
    private val _persistedSession = MutableStateFlow<PersistedSession?>(null)

    /**
     * The session as stored: changes only on start, pause, resume and end, never per
     * second. The session notification watches this.
     */
    val persistedSession: StateFlow<PersistedSession?> = _persistedSession.asStateFlow()

    private val _activeSession = MutableStateFlow<FocusSession?>(null)
    val activeSession: StateFlow<FocusSession?> = _activeSession.asStateFlow()

    private val _interventionConfig = MutableStateFlow(InterventionConfig())
    val interventionConfig: StateFlow<InterventionConfig> = _interventionConfig.asStateFlow()

    private val _dailyStats = MutableStateFlow(DailyStats())
    val dailyStats: StateFlow<DailyStats> = _dailyStats.asStateFlow()

    private val _weeklyBalance = MutableStateFlow<List<WeeklyBalanceDay>>(emptyList())
    val weeklyBalance: StateFlow<List<WeeklyBalanceDay>> = _weeklyBalance.asStateFlow()

    private val _appInterventions = MutableStateFlow<List<AppInterventionStat>>(emptyList())
    val appInterventions: StateFlow<List<AppInterventionStat>> = _appInterventions.asStateFlow()

    /** Foreground time per package per day. Today's entry drives allowance rules. */
    private val _usageHistory = MutableStateFlow<List<DailyUsageSnapshot>>(emptyList())
    val usageHistory: StateFlow<List<DailyUsageSnapshot>> = _usageHistory.asStateFlow()

    /** Focus sessions that have finished, the basis of every focus-time figure. */
    private val _sessionLog = MutableStateFlow<List<FocusSessionRecord>>(emptyList())
    val sessionLog: StateFlow<List<FocusSessionRecord>> = _sessionLog.asStateFlow()

    /** Interceptions recorded by the blocking engine, newest first. */
    private val _distractionAttempts = MutableStateFlow<List<DistractionAttempt>>(emptyList())
    val distractionAttempts: StateFlow<List<DistractionAttempt>> = _distractionAttempts.asStateFlow()

    /** Saved custom sessions such as "Kid session". */
    private val _sessionProfiles = MutableStateFlow<List<SessionProfile>>(emptyList())
    val sessionProfiles: StateFlow<List<SessionProfile>> = _sessionProfiles.asStateFlow()

    /**
     * The running custom session's blocks as rules. Rebuilt only when the session or
     * its profile changes, never per screen check, so the blocking engine reads a ready
     * list on every event at no extra cost.
     */
    @Volatile
    private var cachedSessionRules: List<BlockedAppRule> = emptyList()

    private var timerJob: Job? = null

    /**
     * Completes once state has been read from disk.
     *
     * The accessibility service begins recording foreground time the moment it
     * connects, which can be before hydration finishes. Without this gate those early
     * writes started from an empty list and the persisted history was overwritten,
     * silently wiping the day's screen time.
     */
    private val hydrationComplete = CompletableDeferred<Unit>()

    /** Guards against filing the same completed session more than once. */
    private val completedSessionIds = mutableSetOf<String>()

    /**
     * Every disk write goes through this queue and runs one at a time, in the order the
     * changes were made.
     *
     * Writes used to be launched independently on a multi-threaded scope, so two quick
     * changes could reach the disk in the wrong order and an older snapshot could land
     * last. That is how a switch the user had just flipped could end up flipped back.
     */
    private val writeQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writeQueue) {
                try {
                    write()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save", e)
                }
            }
        }
        scope.launch { hydrate() }
        startTimerLoop()
    }

    /** Suspends until saved state has been read from disk. */
    suspend fun awaitReady() {
        hydrationComplete.await()
    }

    /** Queues a disk write behind every earlier one. Never blocks the caller. */
    private fun persist(write: suspend () -> Unit) {
        writeQueue.trySend(write)
    }

    // ---------------------------------------------------------------- hydration

    private suspend fun hydrate() {
        removeOldDemoData()

        _rules.value = preferences.rulesFlow(emptyList()).first()
        _shortsAndReelsMasterShield.value = preferences.masterShieldFlow(true).first()
        _interventionConfig.value = preferences.interventionFlow(InterventionConfig()).first()
        _isStrictModeEnabled.value = preferences.strictModeFlow(false).first()

        val goal = preferences.goalFlow("").first()
        _dailyStats.update { it.copy(todayGoalText = goal) }

        _sessionProfiles.value = preferences.sessionProfilesFlow().first()
        _persistedSession.value = preferences.sessionFlow().first()
        refreshSessionRules()
        recomputeSession()

        _distractionAttempts.value = preferences.attemptsFlow().first()
        refreshAttemptStats()

        // History is kept so the dashboard can compare days; the allowance counters
        // still only ever read today's entry, so they reset at midnight on their own.
        _usageHistory.value = mergeByDate(preferences.usageHistoryFlow().first())
        _sessionLog.value = preferences.sessionLogFlow().first()
        refreshRuleUsage()
        refreshAttemptStats()

        hydrationComplete.complete(Unit)

        // This repository is the only writer of the DataStore file (the accessibility
        // service shares this same instance), so in-memory state is always the newest.
        // A collector that copied the file back into memory used to overwrite changes
        // still waiting to be saved, making switches flicker back to their old value.
    }

    /**
     * Earlier builds filled a new install with four sample rules (with made-up block
     * counts) and sample texts. Those are removed once so every number in the app is real.
     */
    private suspend fun removeOldDemoData() {
        if (preferences.demoDataRemovedFlow().first()) return
        val rules = preferences.rulesFlow(emptyList()).first()
        val cleaned = rules.filterNot { it.id in OLD_DEMO_RULE_IDS }
        if (cleaned.size != rules.size) preferences.saveRules(cleaned)
        if (preferences.goalFlow("").first() == OLD_DEMO_GOAL) preferences.saveGoal("")
        val config = preferences.interventionFlow(InterventionConfig()).first()
        if (config.customQuote.contains("DSA practice")) {
            preferences.saveIntervention(InterventionConfig())
        }
        preferences.markDemoDataRemoved()
    }

    // ------------------------------------------------------------------- timer

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var tick = 0
            while (true) {
                recomputeSession()
                // Focus time earned by a running session should be visible on Home
                // while it runs, not only once it ends. Recomputed every few seconds
                // rather than every tick because it walks the whole attempt log.
                if (tick % STATS_REFRESH_TICKS == 0) refreshAttemptStats()
                tick++
                delay(1000)
            }
        }
    }

    /**
     * Derives the UI session from the persisted end instant. Because remaining time is
     * computed from the clock rather than decremented, the countdown stays accurate
     * across backgrounding, doze and process death.
     */
    private fun recomputeSession() {
        val persisted = _persistedSession.value
        if (persisted == null) {
            _activeSession.value = null
            return
        }
        val remaining = persisted.remainingSecondsAt(clock())

        // The timer reaching zero is the only moment a session completes on its own,
        // and it has to be filed exactly once or the focus total would keep growing.
        if (remaining <= 0) {
            if (completedSessionIds.add(persisted.id)) recordFinishedSession(completed = true)
            // A finished session is filed and cleared. Leaving it in place made its
            // time count twice: once from the log and once as "still running".
            _persistedSession.value = null
            _activeSession.value = null
            refreshSessionRules()
            refreshAttemptStats()
            persist { preferences.saveSession(null) }
            return
        }

        _activeSession.value = FocusSession(
            id = persisted.id,
            title = persisted.title,
            targetGoal = persisted.targetGoal,
            remainingSeconds = remaining,
            totalDurationSeconds = persisted.totalDurationSeconds,
            isRunning = remaining > 0,
            isPaused = persisted.isPaused,
            blockedApps = persisted.blockedApps,
            endTimeText = formatClockTime(persisted.endsAtEpochMillis),
            profileId = persisted.profileId,
            emoji = persisted.profileId?.let { id ->
                _sessionProfiles.value.firstOrNull { it.id == id }?.emoji
            }.orEmpty(),
            hardLock = persisted.hardLock
        )
    }

    // -------------------------------------------------------------- rules CRUD

    fun addRule(rule: BlockedAppRule) {
        _rules.update { it + rule }
        persistRules()
    }

    fun updateRule(updated: BlockedAppRule) {
        _rules.update { list -> list.map { if (it.id == updated.id) updated else it } }
        persistRules()
    }

    fun deleteRule(ruleId: String) {
        _rules.update { list -> list.filterNot { it.id == ruleId } }
        persistRules()
    }

    fun toggleRule(ruleId: String) {
        _rules.update { list ->
            list.map { rule -> if (rule.id == ruleId) rule.copy(isEnabled = !rule.isEnabled) else rule }
        }
        persistRules()
    }

    fun ruleById(ruleId: String): BlockedAppRule? = _rules.value.firstOrNull { it.id == ruleId }

    /** Switches a one-tap block on (adds its rule) or off (removes it). */
    fun setQuickBlock(quick: com.example.data.model.QuickBlock, enabled: Boolean) {
        val exists = _rules.value.any { it.id == quick.ruleId }
        when {
            enabled && !exists -> addRule(quick.toRule())
            !enabled && exists -> deleteRule(quick.ruleId)
        }
    }

    /**
     * Stores a screen signature captured on this device for [ruleId].
     *
     * Called from the accessibility service when a "Teach this screen" session ends.
     */
    fun applyLearnedSignals(ruleId: String, signals: List<String>) {
        if (signals.isEmpty()) return
        _rules.update { list ->
            list.map { rule ->
                if (rule.id == ruleId) rule.copy(learnedViewIds = signals) else rule
            }
        }
        persistRules()
    }

    private fun persistRules() {
        val snapshot = _rules.value
        persist { preferences.saveRules(snapshot) }
    }

    // ------------------------------------------------------------------ shield

    fun toggleShortsAndReelsMasterShield() {
        val next = !_shortsAndReelsMasterShield.value
        _shortsAndReelsMasterShield.value = next
        persist { preferences.saveMasterShield(next) }
    }

    /** Returns false when refused: Strict Mode cannot be switched off mid-session. */
    fun setStrictMode(enabled: Boolean): Boolean {
        if (!enabled && areRuleChangesLocked()) return false
        _isStrictModeEnabled.value = enabled
        persist { preferences.saveStrictMode(enabled) }
        return true
    }

    /**
     * True when rules must not be edited right now.
     *
     * Strict Mode exists to stop a rule being switched off the moment it becomes
     * inconvenient, so it only bites while a focus session is actually running.
     */
    fun areRuleChangesLocked(): Boolean {
        val session = _activeSession.value ?: return false
        if (!session.isRunning) return false
        // A hard-locked custom session holds until its timer ends; it cannot be paused.
        if (session.hardLock) return true
        if (!_isStrictModeEnabled.value) return false
        return !session.isPaused
    }

    /**
     * True while a focus session is running and not paused. During a session every
     * enabled rule is enforced regardless of its schedule; that is what a session is for.
     */
    fun isFocusSessionActive(): Boolean {
        val session = _activeSession.value ?: return false
        // A custom session applies its own list, not every rule the user has.
        if (session.profileId != null) return false
        return session.isRunning && !session.isPaused
    }

    /**
     * Extra rules from the running custom session, or none. Read by the blocking engine
     * on every event, so it only returns a list built in advance.
     */
    fun activeSessionRules(): List<BlockedAppRule> {
        val persisted = _persistedSession.value ?: return emptyList()
        if (persisted.profileId == null || persisted.isPaused) return emptyList()
        if (persisted.endsAtEpochMillis <= clock()) return emptyList()
        return cachedSessionRules
    }

    private fun refreshSessionRules() {
        val profileId = _persistedSession.value?.profileId
        cachedSessionRules = if (profileId == null) {
            emptyList()
        } else {
            _sessionProfiles.value.firstOrNull { it.id == profileId }?.toRules().orEmpty()
        }
    }

    // ------------------------------------------------------- custom sessions

    /** Adds or updates a custom session. Refused while a hard lock is holding. */
    fun saveSessionProfile(profile: SessionProfile): Boolean {
        if (areRuleChangesLocked()) return false
        _sessionProfiles.update { list ->
            if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it }
            else list + profile
        }
        refreshSessionRules()
        recomputeSession()
        val snapshot = _sessionProfiles.value
        persist { preferences.saveSessionProfiles(snapshot) }
        return true
    }

    /** Refused while a hard lock is holding, or while this session is the one running. */
    fun deleteSessionProfile(profileId: String): Boolean {
        if (areRuleChangesLocked()) return false
        if (_activeSession.value?.profileId == profileId) return false
        _sessionProfiles.update { list -> list.filterNot { it.id == profileId } }
        val snapshot = _sessionProfiles.value
        persist { preferences.saveSessionProfiles(snapshot) }
        return true
    }

    /** Starts a custom session. Refused if any session is already running. */
    fun startProfileSession(profileId: String, durationMinutes: Int): Boolean {
        if (_activeSession.value?.isRunning == true) return false
        val profile = _sessionProfiles.value.firstOrNull { it.id == profileId } ?: return false
        val now = clock()
        val totalSeconds = durationMinutes.coerceAtLeast(1) * 60
        val session = PersistedSession(
            id = "session_" + now,
            title = profile.name,
            targetGoal = _dailyStats.value.todayGoalText,
            totalDurationSeconds = totalSeconds,
            endsAtEpochMillis = now + totalSeconds * 1000L,
            pausedAtEpochMillis = null,
            blockedApps = profile.blocks.map { it.appName + " · " + it.label },
            profileId = profile.id,
            hardLock = profile.lock == SessionLock.HARD
        )
        _persistedSession.value = session
        refreshSessionRules()
        recomputeSession()
        persist { preferences.saveSession(session) }
        return true
    }

    // -------------------------------------------------------------------- goal

    fun updateGoal(newGoal: String) {
        _dailyStats.update { it.copy(todayGoalText = newGoal) }
        _persistedSession.update { it?.copy(targetGoal = newGoal) }
        recomputeSession()
        val session = _persistedSession.value
        persist {
            preferences.saveGoal(newGoal)
            preferences.saveSession(session)
        }
    }

    // ----------------------------------------------------------------- session

    fun startDeepFocus(durationMinutes: Int) {
        val now = clock()
        val totalSeconds = durationMinutes * 60
        val session = PersistedSession(
            id = "session_" + now,
            title = "Focus session",
            targetGoal = _dailyStats.value.todayGoalText,
            totalDurationSeconds = totalSeconds,
            endsAtEpochMillis = now + totalSeconds * 1000L,
            pausedAtEpochMillis = null,
            blockedApps = _rules.value
                .filter { it.isEnabled }
                .map { it.appName + " · " + it.filterLabel }
        )
        _persistedSession.value = session
        recomputeSession()
        persist { preferences.saveSession(session) }
    }

    /** Returns false when refused: Strict Mode does not allow ending a session early. */
    fun endActiveSession(): Boolean {
        val running = _activeSession.value?.isRunning == true
        if (running && areRuleChangesLocked()) return false
        recordFinishedSession(completed = false)
        _persistedSession.value = null
        _activeSession.value = null
        refreshSessionRules()
        refreshAttemptStats()
        persist { preferences.saveSession(null) }
        return true
    }

    /** Returns false when refused: a strict session cannot be paused. */
    fun togglePauseSession(): Boolean {
        if (areRuleChangesLocked()) return false
        val now = clock()
        _persistedSession.update { current ->
            when {
                current == null -> null
                current.isPaused -> current.resumedAt(now)
                else -> current.copy(pausedAtEpochMillis = now)
            }
        }
        recomputeSession()
        val session = _persistedSession.value
        persist { preferences.saveSession(session) }
        return true
    }

    // ------------------------------------------------------------ app usage

    /**
     * Adds foreground time for a package. Called by the accessibility service whenever
     * the user leaves an app, and periodically while they stay in one.
     */
    fun addForegroundTime(packageName: String, millis: Long) {
        if (millis <= 0L) return
        scope.launch {
            hydrationComplete.await()
            applyForegroundTime(packageName, millis)
        }
    }

    private fun applyForegroundTime(packageName: String, millis: Long) {
        val today = todayKey()
        _usageHistory.update { history ->
            val existing = history.firstOrNull { it.dateKey == today }
                ?: DailyUsageSnapshot(today)
            val updated = existing.plus(packageName, millis)
            mergeByDate(listOf(updated) + history.filterNot { it.dateKey == today })
                .take(MAX_USAGE_DAYS)
        }
        refreshRuleUsage()
        refreshAttemptStats()

        val historySnapshot = _usageHistory.value
        val rulesSnapshot = _rules.value
        persist {
            preferences.saveUsageHistory(historySnapshot)
            // Persist the rules too, so the counter survives a restart instead of
            // reverting to the value from the last rule edit.
            preferences.saveRules(rulesSnapshot)
        }
    }

    /**
     * Collapses any duplicate day entries into one.
     *
     * A day must appear at most once or every total built from the history counts that
     * day twice. Duplicates can survive a write that raced with hydration, so the list
     * is normalised on the way in rather than trusted.
     */
    private fun mergeByDate(history: List<DailyUsageSnapshot>): List<DailyUsageSnapshot> =
        history.groupBy { it.dateKey }
            .map { (dateKey, sameDay) ->
                if (sameDay.size == 1) {
                    sameDay.first()
                } else {
                    val merged = mutableMapOf<String, Long>()
                    sameDay.forEach { snapshot ->
                        snapshot.millisByPackage.forEach { (key, millis) ->
                            merged[key] = (merged[key] ?: 0L) + millis
                        }
                    }
                    DailyUsageSnapshot(dateKey, merged)
                }
            }
            .sortedByDescending { it.dateKey }

    fun todayUsage(): DailyUsageSnapshot =
        _usageHistory.value.firstOrNull { it.dateKey == todayKey() }
            ?: DailyUsageSnapshot(todayKey())

    fun usedMinutesToday(packageName: String): Int = todayUsage().minutesFor(packageName)

    /** Mirrors today's usage onto the rules so the allowance progress bars are real. */
    private fun refreshRuleUsage() {
        val usage = todayUsage()
        _rules.update { list ->
            list.map { rule ->
                val used = usage.minutesFor(rule.packageName)
                if (rule.usedMinutes == used) rule else rule.copy(usedMinutes = used)
            }
        }
    }

    private fun todayKey(): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return format.format(java.util.Date(clock()))
    }

    // ------------------------------------------------------- distraction log

    /**
     * Records an interception and bumps the matching rule's counter.
     *
     * Called from the accessibility service, so it must be safe off the main thread and
     * must not block: state is updated in memory immediately and persisted on the
     * application scope.
     */
    fun recordDistractionAttempt(
        packageName: String,
        appName: String,
        ruleId: String,
        ruleLabel: String,
        blockMode: BlockMode,
        sessionId: String?,
        nowMillis: Long = clock()
    ) {
        scope.launch {
            hydrationComplete.await()
            applyDistractionAttempt(
                packageName, appName, ruleId, ruleLabel, blockMode, sessionId, nowMillis
            )
        }
    }

    private fun applyDistractionAttempt(
        packageName: String,
        appName: String,
        ruleId: String,
        ruleLabel: String,
        blockMode: BlockMode,
        sessionId: String?,
        nowMillis: Long
    ) {
        val attempt = DistractionAttempt(
            id = "attempt_" + nowMillis,
            packageName = packageName,
            appName = appName,
            timestampEpochMillis = nowMillis,
            ruleId = ruleId,
            ruleLabel = ruleLabel,
            sessionId = sessionId,
            blockMode = blockMode
        )

        // Newest first, capped so the persisted JSON cannot grow without bound.
        _distractionAttempts.update { (listOf(attempt) + it).take(MAX_STORED_ATTEMPTS) }
        _rules.update { list ->
            list.map { rule ->
                if (rule.id == ruleId) {
                    rule.copy(attemptsBlockedCount = rule.attemptsBlockedCount + 1)
                } else rule
            }
        }
        refreshAttemptStats()

        val attempts = _distractionAttempts.value
        val rules = _rules.value
        persist {
            preferences.saveAttempts(attempts)
            preferences.saveRules(rules)
        }
    }

    /** Recomputes every dashboard figure from the real logs. */
    private fun refreshAttemptStats() {
        val attempts = _distractionAttempts.value
        val now = clock()

        _dailyStats.value = FocusAnalytics.dailyStats(
            base = _dailyStats.value,
            attempts = attempts,
            sessions = _sessionLog.value,
            usageHistory = _usageHistory.value,
            rules = _rules.value,
            runningSessionSeconds = elapsedSecondsOfRunningSession(now),
            nowMillis = now
        )

        _appInterventions.value = FocusAnalytics.appInterventions(attempts)
        _weeklyBalance.value = FocusAnalytics.weeklyBalance(attempts, _sessionLog.value, now)
    }

    /** Focus already banked by the session in progress, so Home updates live. */
    private fun elapsedSecondsOfRunningSession(nowMillis: Long): Int {
        val persisted = _persistedSession.value ?: return 0
        // Custom sessions (e.g. a child's phone time) are not the user's own focus time.
        if (persisted.profileId != null) return 0
        val remaining = persisted.remainingSecondsAt(nowMillis)
        return (persisted.totalDurationSeconds - remaining).coerceAtLeast(0)
    }

    /** Files a finished session so its focus time counts towards every figure. */
    private fun recordFinishedSession(completed: Boolean) {
        val persisted = _persistedSession.value ?: return
        if (persisted.profileId != null) return
        val now = clock()
        val remaining = persisted.remainingSecondsAt(now)
        val focusedSeconds = (persisted.totalDurationSeconds - remaining).coerceAtLeast(0)
        if (focusedSeconds <= 0) return

        val record = FocusSessionRecord(
            id = persisted.id,
            startedAtEpochMillis = persisted.endsAtEpochMillis -
                persisted.totalDurationSeconds * 1000L,
            endedAtEpochMillis = now,
            focusedSeconds = focusedSeconds,
            completed = completed
        )

        _sessionLog.update { (listOf(record) + it).take(MAX_SESSION_RECORDS) }
        val snapshot = _sessionLog.value
        persist { preferences.saveSessionLog(snapshot) }
    }

    // ------------------------------------------------------------ intervention

    fun updateInterventionConfig(updated: InterventionConfig) {
        _interventionConfig.value = updated
        persist { preferences.saveIntervention(updated) }
    }

    // ----------------------------------------------------------------- helpers

    private fun formatClockTime(epochMillis: Long): String {
        val format = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return format.format(java.util.Date(epochMillis))
    }

    private companion object {
        const val TAG = "FocusGuardRepo"
        const val MAX_STORED_ATTEMPTS = 500
        const val MAX_SESSION_RECORDS = 500
        const val MAX_USAGE_DAYS = 60

        /** Seconds between dashboard recomputes while a session runs. */
        const val STATS_REFRESH_TICKS = 5

        /** Ids of the sample rules older builds created on first launch. */
        val OLD_DEMO_RULE_IDS = setOf("rule_instagram", "rule_youtube", "rule_tiktok", "rule_twitter")
        const val OLD_DEMO_GOAL = "Finish my DSA practice before 7 PM."
    }
}
