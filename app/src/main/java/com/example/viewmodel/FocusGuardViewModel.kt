package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.FocusGuardApplication
import com.example.data.analytics.FocusAnalytics
import com.example.data.model.BlockMode
import com.example.data.model.BlockedAppRule
import com.example.data.model.InsightsStats
import com.example.data.model.InterventionConfig
import com.example.data.model.SessionProfile
import com.example.data.model.Timeframe
import com.example.data.repository.FocusGuardRepository
import com.example.data.repository.InstalledApp
import com.example.data.repository.InstalledAppsProvider
import com.example.service.ScreenLearner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FocusGuardViewModel(
    private val repository: FocusGuardRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val screenLearner: ScreenLearner
) : ViewModel() {

    val rules = repository.rules
    val activeSession = repository.activeSession
    val interventionConfig = repository.interventionConfig
    val dailyStats = repository.dailyStats
    val isStrictModeEnabled = repository.isStrictModeEnabled
    val sessionProfiles = repository.sessionProfiles

    private val _selectedDurationMinutes = MutableStateFlow(DEFAULT_SESSION_MINUTES)
    val selectedDurationMinutes: StateFlow<Int> = _selectedDurationMinutes.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.THIS_WEEK)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    /** Everything Insights shows, recomputed whenever the logs or the timeframe change. */
    val insightsStats: StateFlow<InsightsStats> = combine(
        repository.distractionAttempts,
        repository.sessionLog,
        repository.usageHistory,
        repository.activeSession,
        _selectedTimeframe
    ) { attempts, sessions, usage, active, timeframe ->
        val runningSeconds = active
            ?.takeIf { it.profileId == null }
            ?.let { (it.totalDurationSeconds - it.remainingSeconds).coerceAtLeast(0) }
            ?: 0
        FocusAnalytics.insights(
            timeframe = timeframe,
            attempts = attempts,
            sessions = sessions,
            usageHistory = usage,
            runningSessionSeconds = runningSeconds,
            nowMillis = System.currentTimeMillis()
        )
    }
        // Walks every log, and re-runs every second while a session ticks. Kept off the
        // main thread so it can never cost a frame.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsStats())

    private val _isAddBlockSheetOpen = MutableStateFlow(false)
    val isAddBlockSheetOpen: StateFlow<Boolean> = _isAddBlockSheetOpen.asStateFlow()

    private val _isEditGoalDialogOpen = MutableStateFlow(false)
    val isEditGoalDialogOpen: StateFlow<Boolean> = _isEditGoalDialogOpen.asStateFlow()

    /** The rule the Add/Edit sheet is editing; null means the sheet is creating one. */
    private val _ruleBeingEdited = MutableStateFlow<BlockedAppRule?>(null)
    val ruleBeingEdited: StateFlow<BlockedAppRule?> = _ruleBeingEdited.asStateFlow()

    /** Every launchable app on the device, for the block picker. Loaded on first use. */
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isLoadingInstalledApps = MutableStateFlow(false)
    val isLoadingInstalledApps: StateFlow<Boolean> = _isLoadingInstalledApps.asStateFlow()

    /** Rule pending delete confirmation, so a destructive tap is never one-click. */
    private val _rulePendingDelete = MutableStateFlow<BlockedAppRule?>(null)
    val rulePendingDelete: StateFlow<BlockedAppRule?> = _rulePendingDelete.asStateFlow()

    /**
     * Reading every installed app and its icon is slow, so it happens only when the
     * block picker is opened, not every time a screen using this ViewModel appears.
     */
    private fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty() || _isLoadingInstalledApps.value) return
        viewModelScope.launch {
            _isLoadingInstalledApps.value = true
            _installedApps.value = installedAppsProvider.loadInstalledApps()
            _isLoadingInstalledApps.value = false
        }
    }

    /**
     * Starts reading the installed apps in the background as soon as Blocks is shown,
     * so the app picker is already filled by the time the user taps "Add".
     */
    fun prefetchInstalledApps() = loadInstalledApps()

    // ------------------------------------------------------------- session

    fun selectDuration(minutes: Int) {
        _selectedDurationMinutes.value = minutes
    }

    fun startFocusSession() {
        repository.startDeepFocus(_selectedDurationMinutes.value)
    }

    /** False if another session is already running. */
    fun startProfileSession(profileId: String, minutes: Int): Boolean =
        repository.startProfileSession(profileId, minutes)

    /** False while a hard lock or Strict Mode is holding. */
    fun saveSessionProfile(profile: SessionProfile): Boolean = repository.saveSessionProfile(profile)

    /** False while locked, or while that session is the one running. */
    fun deleteSessionProfile(profileId: String): Boolean = repository.deleteSessionProfile(profileId)

    /** True while edits are refused (Strict Mode, or a hard-locked session). */
    fun areChangesLocked(): Boolean = repository.areRuleChangesLocked()

    /** Starts loading installed apps for the session editor's app picker. */
    fun loadAppsForPicker() = loadInstalledApps()

    /** False when Strict Mode refuses to end the session early. */
    fun endSession(): Boolean = repository.endActiveSession()

    /** False when Strict Mode refuses to pause the session. */
    fun togglePauseSession(): Boolean = repository.togglePauseSession()

    fun updateGoal(newGoal: String) {
        repository.updateGoal(newGoal.trim())
        _isEditGoalDialogOpen.value = false
    }

    fun setEditGoalDialogOpen(open: Boolean) {
        _isEditGoalDialogOpen.value = open
    }

    // ------------------------------------------------------------- settings

    fun selectTimeframe(timeframe: Timeframe) {
        _selectedTimeframe.value = timeframe
    }

    /** False when refused: Strict Mode cannot be switched off during a strict session. */
    fun setStrictMode(enabled: Boolean): Boolean = repository.setStrictMode(enabled)

    /** Strict Mode blocks rule edits while a session runs; surfaced so the UI can say so. */
    fun areRuleChangesLocked(): Boolean = repository.areRuleChangesLocked()

    /** Turns a one-tap block on or off. Strict Mode only refuses switching one off. */
    fun setQuickBlock(quick: com.example.data.model.QuickBlock, enabled: Boolean) {
        if (!enabled && repository.areRuleChangesLocked()) return
        repository.setQuickBlock(quick, enabled)
    }

    fun updateInterventionConfig(config: InterventionConfig) {
        repository.updateInterventionConfig(config)
    }

    // ---------------------------------------------------------------- rules

    fun toggleRule(ruleId: String) {
        if (repository.areRuleChangesLocked()) return
        repository.toggleRule(ruleId)
    }

    fun setAddBlockSheetOpen(open: Boolean) {
        _isAddBlockSheetOpen.value = open
        if (!open) _ruleBeingEdited.value = null
    }

    /** Opens the sheet in "create" mode. */
    fun startCreatingRule() {
        if (repository.areRuleChangesLocked()) return
        loadInstalledApps()
        _ruleBeingEdited.value = null
        _isAddBlockSheetOpen.value = true
    }

    /** Opens the sheet prefilled with an existing rule. */
    fun startEditingRule(ruleId: String) {
        if (repository.areRuleChangesLocked()) return
        loadInstalledApps()
        _ruleBeingEdited.value = repository.ruleById(ruleId)
        _isAddBlockSheetOpen.value = true
    }

    /**
     * Single save path for the Add/Edit sheet. Creates a new rule or updates the one
     * being edited, preserving its id and accumulated counters.
     */
    fun saveRule(
        appName: String,
        packageName: String,
        mode: BlockMode,
        filterLabel: String,
        schedule: String,
        allowanceMinutes: Int = DEFAULT_ALLOWANCE_MINUTES,
        blockedContentIds: List<String> = emptyList(),
        contentAllowanceMinutes: Int = 0,
        showPauseScreen: Boolean = true
    ): String? {
        if (repository.areRuleChangesLocked()) return null
        val existing = _ruleBeingEdited.value
        val resolvedPackage = packageName.ifBlank { existing?.packageName.orEmpty() }
        if (resolvedPackage.isBlank()) return null
        // Only an allowance rule carries a budget.
        val resolvedAllowance = if (mode == BlockMode.DAILY_ALLOWANCE) {
            allowanceMinutes.takeIf { it > 0 } ?: DEFAULT_ALLOWANCE_MINUTES
        } else 0

        val ruleId = existing?.id ?: ("rule_" + System.currentTimeMillis())

        if (existing == null) {
            repository.addRule(
                BlockedAppRule(
                    id = ruleId,
                    appName = appName,
                    packageName = resolvedPackage,
                    blockMode = mode,
                    filterLabel = filterLabel,
                    scheduleText = schedule,
                    isEnabled = true,
                    totalAllowedMinutes = resolvedAllowance,
                    blockedContentIds = blockedContentIds,
                    contentAllowanceMinutes = contentAllowanceMinutes,
                    showPauseScreen = showPauseScreen
                )
            )
        } else {
            repository.updateRule(
                existing.copy(
                    appName = appName,
                    packageName = resolvedPackage,
                    blockMode = mode,
                    filterLabel = filterLabel,
                    scheduleText = schedule,
                    totalAllowedMinutes = resolvedAllowance,
                    blockedContentIds = blockedContentIds,
                    contentAllowanceMinutes = contentAllowanceMinutes,
                    showPauseScreen = showPauseScreen
                )
            )
        }
        _isAddBlockSheetOpen.value = false
        _ruleBeingEdited.value = null
        return ruleId
    }

    /**
     * Saves the rule, then starts a capture window so the user can open the app and
     * navigate to the screen they want blocked.
     */
    fun teachScreen(
        appName: String,
        packageName: String,
        mode: BlockMode,
        filterLabel: String,
        schedule: String,
        blockedContentIds: List<String>,
        contentAllowanceMinutes: Int
    ): Long {
        val ruleId = saveRule(
            appName = appName,
            packageName = packageName,
            mode = mode,
            filterLabel = filterLabel,
            schedule = schedule,
            blockedContentIds = blockedContentIds,
            contentAllowanceMinutes = contentAllowanceMinutes
        ) ?: return 0L

        screenLearner.start(ruleId, packageName, TEACH_WINDOW_MILLIS)
        return TEACH_WINDOW_MILLIS
    }

    fun requestDeleteRule(ruleId: String) {
        if (repository.areRuleChangesLocked()) return
        _rulePendingDelete.value = repository.ruleById(ruleId)
    }

    fun cancelDeleteRule() {
        _rulePendingDelete.value = null
    }

    fun confirmDeleteRule() {
        _rulePendingDelete.value?.let { repository.deleteRule(it.id) }
        _rulePendingDelete.value = null
    }

    companion object {
        private const val DEFAULT_ALLOWANCE_MINUTES = 30
        private const val DEFAULT_SESSION_MINUTES = 25

        /** How long the user has to reach the screen they want captured. */
        const val TEACH_WINDOW_MILLIS = 20_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FocusGuardApplication
                FocusGuardViewModel(
                    repository = app.container.repository,
                    installedAppsProvider = app.container.installedAppsProvider,
                    screenLearner = app.container.screenLearner
                )
            }
        }
    }
}
