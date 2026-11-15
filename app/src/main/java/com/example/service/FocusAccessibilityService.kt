package com.example.service

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.accessibility.AccessibilityEvent
import com.example.FocusGuardApplication
import com.example.data.model.BlockDecision
import com.example.data.model.BlockMode
import com.example.data.model.ContentCatalog
import com.example.data.model.ContentDetectionResult
import com.example.data.model.ContentTarget
import com.example.data.repository.FocusGuardRepository
import com.example.service.detection.ContentDetectionEngine
import com.example.service.detection.NodeScanner
import com.example.ui.intervention.InterventionActivity

/**
 * Observes foreground window changes and hands each package to [BlockingEngine].
 *
 * This class is intentionally thin. It knows how to read the system and how to perform
 * effects (leave the app, show the intervention); every decision about *whether* to
 * block lives in the engine, which has no Android dependencies.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val detectionEngine = ContentDetectionEngine()
    private var lastContentScanAtMillis = 0L

    /** Shared with the intervention screen; see [BlockSuppressor]. */
    private var suppressor: BlockSuppressor? = null

    /** Active while the user is teaching UltimateFocus a screen. */
    private var screenLearner: ScreenLearner? = null

    /** Last time each content surface was blocked, for detecting a block loop. */
    private val lastContentBlockAt = mutableMapOf<String, Long>()

    /** Content surface currently on screen, and when it appeared. */
    private var currentContentKey: String? = null
    private var contentSinceMillis = 0L
    private var lastProbeAtMillis = 0L

    private var repository: FocusGuardRepository? = null
    private var blockingEngine: BlockingEngine? = null

    private var lastHandledPackage: String? = null
    private var lastInterventionAtMillis = 0L

    /** Package currently in the foreground, and when it got there. */
    private var foregroundPackage: String? = null
    private var foregroundSinceMillis = 0L

    private var usageTicker: Job? = null

    /** When each rule last plain-blocked, to notice BACK not getting the user out. */
    private val lastPlainBlockAt = mutableMapOf<String, Long>()
    private var lastPlainToastAtMillis = 0L

    /** The app most recently blocked, so its picture-in-picture player can be closed. */
    @Volatile
    private var recentlyBlockedPackage: String? = null
    private var recentlyBlockedAtMillis = 0L

    /** Set once a payment pause starts, so no event slips through while unbinding. */
    @Volatile
    private var isPausingForPayment = false

    /**
     * Re-checks an app with a part-of-app block while the user is in it.
     *
     * Apps such as YouTube restore Shorts a moment *after* they open, and a playing
     * Short sends almost no screen-change events. Without these checks, reopening the
     * app straight into Shorts went unnoticed until the user scrolled.
     */
    private val mainHandler = Handler(Looper.getMainLooper())
    private var contentWatchPackage: String? = null
    private val contentWatch = object : Runnable {
        override fun run() {
            val watched = contentWatchPackage ?: return
            if (!recheckContent(watched)) {
                contentWatchPackage = null
                return
            }
            mainHandler.postDelayed(this, CONTENT_WATCH_INTERVAL_MILLIS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = FocusGuardApplication.container()
        if (container == null) {
            // Should not happen: Application.onCreate always runs before a service in
            // the same process. Logged rather than crashed so accessibility stays alive.
            Log.e(TAG, "AppContainer unavailable; blocking disabled for this connection")
            return
        }
        repository = container.repository
        blockingEngine = container.blockingEngine
        suppressor = container.blockSuppressor
        screenLearner = container.screenLearner
        isPausingForPayment = false
        PaymentPause.pauseHandler = { trigger -> pauseForPayment(trigger) }
        // Connecting means the user switched the shield back on: clear the reminder.
        PaymentPause.onResumed(this)
        seedForegroundFromActiveWindow()
        startUsageTicker()
        Log.i(TAG, "UltimateFocus accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // A floating picture-in-picture player can appear without any app coming to the
        // foreground, so it is checked on every change to the set of windows.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            closeBlockedPictureInPicture()
            return
        }

        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!isWindowChange && !isContentChange) return

        val packageName = event.packageName?.toString() ?: return
        val engine = blockingEngine ?: return
        val now = System.currentTimeMillis()

        // Payment and banking apps refuse to run while this service is on, so step
        // aside before they finish opening. See PaymentPause for the full reasoning.
        if (isPausingForPayment) return
        if (isWindowChange && shouldPauseForPayment(packageName)) {
            pauseForPayment(packageName)
            return
        }

        // While teaching, record what the screen exposes and block nothing: an
        // intervention here would interrupt the very navigation being captured.
        if (handleLearning(packageName)) return

        // Leaving the blocked app (home screen, another app) ends its grace window, so
        // coming back to the feed is caught at once. The pause screen itself is ours and
        // must not end it.
        if (isWindowChange && packageName != applicationContext.packageName) {
            suppressor?.clearIfNot(packageName)
        }

        // Keep checking apps with a part-of-app block for as long as the user is in them.
        if (hasContentRuleFor(packageName)) {
            startContentWatch(packageName, restart = isWindowChange)
        }

        // The grace window after a block stops the intervention re-firing while the app
        // is still showing the blocked screen. It must NOT be a blanket pause on the
        // app: sitting out the window and opening the next reel used to slip straight
        // through. So the decision is still computed while suppressed, and the moment
        // the blocked content is off screen the window is dropped.
        val wasSuppressed = suppressor?.isSuppressed(packageName) == true

        // Content-changed fires constantly while a feed scrolls. Only re-scan the node
        // tree for apps that actually have a detector, and never more than once per
        // throttle window, so event delivery for the whole device stays responsive.
        if (isContentChange) {
            if (!hasContentRuleFor(packageName)) return
            if (now - lastContentScanAtMillis < CONTENT_SCAN_THROTTLE_MILLIS) return
            lastContentScanAtMillis = now
        }

        // Bank the time spent in the app the user is leaving before judging the new one,
        // so a daily allowance is measured against real foreground time.
        if (isWindowChange) {
            trackForegroundChange(packageName, now)
        }

        val decision = engine.evaluate(packageName, now) { targets ->
            detectContent(packageName, targets)
        }

        if (!decision.shouldBlock) {
            // Reset the guard as soon as the user leaves the blocked app, so returning
            // to it triggers a fresh intervention.
            if (decision.reason != BlockDecision.Reason.IGNORED_PACKAGE) {
                lastHandledPackage = null
            }
            // Off the blocked screen: end the grace window now so the next reel is
            // caught immediately instead of riding out the remaining seconds.
            if (wasSuppressed) {
                suppressor?.clear()
                Log.d(TAG, "Left blocked content in $packageName; grace window cleared")
            }
            Log.d(TAG, "Allow $packageName (${decision.reason})")
            return
        }

        // Still on the blocked screen inside the grace window: stay quiet so the
        // intervention does not loop, but keep the window open.
        if (wasSuppressed) return

        handleBlock(packageName, decision)
    }

    private fun handleBlock(packageName: String, decision: BlockDecision) {
        val rule = decision.rule ?: return
        val now = System.currentTimeMillis()
        // A plain block has no screen to wait for, so it can act again almost at once:
        // someone tapping Shorts twice in a row must be stopped both times.
        val cooldown = if (rule.showPauseScreen) INTERVENTION_COOLDOWN_MILLIS else PLAIN_COOLDOWN_MILLIS
        val isRepeat = packageName == lastHandledPackage && now - lastInterventionAtMillis < cooldown
        if (isRepeat) return

        lastHandledPackage = packageName
        lastInterventionAtMillis = now
        recentlyBlockedPackage = packageName
        recentlyBlockedAtMillis = now

        Log.i(TAG, "BLOCK $packageName via rule ${rule.id} (${rule.filterLabel})")

        repository?.recordDistractionAttempt(
            packageName = packageName,
            appName = rule.appName,
            ruleId = rule.id,
            ruleLabel = rule.filterLabel,
            blockMode = rule.blockMode,
            sessionId = decision.sessionId,
            nowMillis = now
        )

        val isContentBlock = rule.blockMode == BlockMode.CONTENT_LEVEL

        if (!rule.showPauseScreen) {
            plainBlock(packageName, rule, isContentBlock, now)
            return
        }

        // A second block of the same surface soon after the first means navigating away
        // did not work — a tab-based feed such as Snapchat Spotlight does not respond to
        // BACK, and reopening the app restores the same tab. Escalating breaks the loop.
        var isRepeatBlock = false
        if (isContentBlock) {
            // A rule maps to a fixed set of content targets, so the rule id is a
            // precise enough key for spotting a repeat of the same surface.
            val contentKey = packageName + "::" + rule.id
            val previous = lastContentBlockAt[contentKey]
            isRepeatBlock = previous != null &&
                now - previous < BlockSuppressor.LOOP_WINDOW_MILLIS
            lastContentBlockAt[contentKey] = now
        }

        suppressor?.suppress(
            packageName,
            if (isRepeatBlock) BlockSuppressor.LOOP_ESCAPE_MILLIS
            else BlockSuppressor.POST_BLOCK_MILLIS
        )

        if (isContentBlock && !isRepeatBlock) {
            // Steer the app out of the feed so dismissing the intervention lands the
            // user back in the usable part of the app. Skipped on a repeat, where this
            // has already been shown not to help.
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        showIntervention(packageName, rule, isContentBlock, isRepeatBlock)

        // Leaving a video app often turns the blocked video into a floating player a
        // moment later. Windows-changed events catch most of these; these checks catch
        // devices that report the change late or not at all.
        serviceScope.launch {
            for (delayMillis in PIP_CHECK_DELAYS_MILLIS) {
                delay(delayMillis)
                closeBlockedPictureInPicture()
            }
        }
    }

    /**
     * Blocks without the pause screen: leaves the blocked part and says so in one line.
     *
     * For a part of an app this is BACK, which takes YouTube from Shorts to Home and
     * Instagram from Reels to the feed. If the same feed is blocked again within moments,
     * BACK did not get the user out (some feeds ignore it), so the user is sent to the
     * home screen instead. A whole-app block or a used-up daily limit always goes home.
     */
    private fun plainBlock(
        packageName: String,
        rule: com.example.data.model.BlockedAppRule,
        isContentBlock: Boolean,
        now: Long
    ) {
        val key = packageName + "::" + rule.id
        val previous = lastPlainBlockAt[key]
        val backDidNotWork = previous != null && now - previous < PLAIN_LOOP_WINDOW_MILLIS
        lastPlainBlockAt[key] = now

        suppressor?.suppress(packageName, PLAIN_SUPPRESS_MILLIS)
        if (isContentBlock && !backDidNotWork) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        if (now - lastPlainToastAtMillis > PLAIN_TOAST_GAP_MILLIS) {
            lastPlainToastAtMillis = now
            showToast(rule.filterLabel + " blocked", long = false)
        }

        serviceScope.launch {
            for (delayMillis in PIP_CHECK_DELAYS_MILLIS) {
                delay(delayMillis)
                closeBlockedPictureInPicture()
            }
        }
    }

    /**
     * Closes picture-in-picture players that show something blocked.
     *
     * A floating window is closed when it belongs to the app that was just blocked (it is
     * almost certainly the blocked video, shrunk as the app was sent away), or when its
     * own content is blocked right now, such as a Short started from somewhere else.
     * Picture-in-picture for anything not blocked is left alone.
     */
    private fun closeBlockedPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val engine = blockingEngine ?: return
        val allWindows = try {
            windows
        } catch (e: Exception) {
            return
        } ?: return

        val now = System.currentTimeMillis()
        for (window in allWindows) {
            if (!window.isInPictureInPictureMode) continue
            val root = try {
                window.root
            } catch (e: Exception) {
                null
            }
            val pipPackage = root?.packageName?.toString()

            val justBlocked = pipPackage != null &&
                pipPackage == recentlyBlockedPackage &&
                now - recentlyBlockedAtMillis < PIP_FOLLOW_UP_MILLIS
            val blockedNow = pipPackage != null && !justBlocked &&
                engine.evaluate(pipPackage, now) { targets ->
                    try {
                        detectionEngine.detect(root, targets)
                    } catch (e: Exception) {
                        null
                    }
                }.shouldBlock

            if (!justBlocked && !blockedNow) continue

            Log.i(TAG, "Closing picture-in-picture for " + pipPackage)
            val dismissed = try {
                root?.performAction(AccessibilityNodeInfo.ACTION_DISMISS) == true
            } catch (e: Exception) {
                false
            }
            if (!dismissed) {
                // Some phones do not offer dismiss on the floating window. Pausing the
                // player at least stops the video; the window is harmless once paused.
                pauseMediaPlayback()
            }
        }
    }

    /** Sends a media "pause" key, which the playing video app responds to. */
    private fun pauseMediaPlayback() {
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val time = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(
                KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
            )
            audio.dispatchMediaKeyEvent(
                KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not pause playback", e)
        }
    }

    /**
     * Presents the intervention over the blocked app.
     *
     * Deliberately does NOT call performGlobalAction(GLOBAL_ACTION_HOME) first.
     * That action is asynchronous, so it lands *after* this Activity starts and
     * dismisses the intervention again, leaving the user on the launcher and
     * re-triggering the block in a loop.
     *
     * The intervention is a full-screen Activity in its own task, so it covers the
     * blocked app on its own. If the user backs out of it, the blocked app returns to
     * the foreground, which emits a fresh window event and blocks it again — the shield
     * stays up rather than being a one-shot.
     */
    private fun showIntervention(
        packageName: String,
        rule: com.example.data.model.BlockedAppRule,
        isContentBlock: Boolean,
        isRepeatBlock: Boolean
    ) {
        // The BACK action above is asynchronous. Starting the Activity in the same
        // breath would race it and the navigation would land on the intervention
        // instead of the app, so a content block waits for BACK to settle first.
        val startDelay = if (isContentBlock) BACK_SETTLE_DELAY_MILLIS else 0L
        serviceScope.launch {
            if (startDelay > 0L) delay(startDelay)
            try {
                startActivity(
                    InterventionActivity.intent(
                        context = this@FocusAccessibilityService,
                        blockedPackage = packageName,
                        blockedAppName = rule.appName,
                        blockMode = rule.blockMode,
                        ruleLabel = rule.filterLabel,
                        isContentBlock = isContentBlock,
                        isRepeatBlock = isRepeatBlock
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Could not show intervention for $packageName", e)
            }
        }
    }

    /**
     * Accumulates foreground time for the package being left.
     *
     * Android only notifies us on window changes, so without the ticker below a user who
     * sits in one app forever would never have their allowance counted.
     */
    private fun trackForegroundChange(newPackage: String, nowMillis: Long) {
        val previous = foregroundPackage
        if (previous == newPackage) return
        flushForegroundTime(nowMillis)
        flushContentTime(nowMillis)
        foregroundPackage = newPackage
        foregroundSinceMillis = nowMillis
    }

    private fun flushForegroundTime(nowMillis: Long) {
        val previous = foregroundPackage ?: return
        val elapsed = nowMillis - foregroundSinceMillis
        if (elapsed <= 0L) return
        if (previous == packageName) return
        if (previous in DefaultBlockingEngine.DEFAULT_IGNORED_PACKAGES) return
        repository?.addForegroundTime(previous, elapsed)
        foregroundSinceMillis = nowMillis
    }

    /**
     * Starts (or keeps) the repeated check of [watched]. On opening the app the first
     * check comes quickly, to catch a feed the app restores while it settles.
     */
    private fun startContentWatch(watched: String, restart: Boolean) {
        if (contentWatchPackage == watched && !restart) return
        contentWatchPackage = watched
        mainHandler.removeCallbacks(contentWatch)
        mainHandler.postDelayed(contentWatch, CONTENT_WATCH_FIRST_MILLIS)
    }

    private fun stopContentWatch() {
        contentWatchPackage = null
        mainHandler.removeCallbacks(contentWatch)
    }

    /**
     * One check of the app on screen, with the same rules as a screen-change event.
     * Returns false when the user is no longer in [watched], which ends the watch.
     */
    private fun recheckContent(watched: String): Boolean {
        if (isPausingForPayment) return false
        if (screenLearner?.isActive == true) return true
        val onScreen = resolveActiveWindowPackage() ?: return true
        if (onScreen != watched) return false
        if (!hasContentRuleFor(watched)) return false
        val engine = blockingEngine ?: return false

        val now = System.currentTimeMillis()
        val wasSuppressed = suppressor?.isSuppressed(watched) == true
        val decision = engine.evaluate(watched, now) { targets -> detectContent(watched, targets) }
        if (!decision.shouldBlock) {
            if (wasSuppressed) suppressor?.clear()
            return true
        }
        if (wasSuppressed) return true
        handleBlock(watched, decision)
        return true
    }

    /**
     * True when an enabled content rule targets this package.
     *
     * Content-changed events fire constantly, so scanning is limited to apps the user
     * actually asked to filter rather than to a fixed list of known apps.
     */
    private fun hasContentRuleFor(packageName: String): Boolean {
        val repo = repository ?: return false
        val matches = { rule: com.example.data.model.BlockedAppRule ->
            rule.isEnabled &&
                rule.blockMode == BlockMode.CONTENT_LEVEL &&
                rule.packageName.equals(packageName, ignoreCase = true)
        }
        // A running custom session's part-of-app blocks count too.
        return repo.rules.value.any(matches) || repo.activeSessionRules().any(matches)
    }

    /**
     * True when [packageName] is a payment or banking app the shield should step aside
     * for. A rule the user wrote for that exact app wins while it is blocking: someone
     * who blocks Paytm on purpose (it also carries games and shopping) keeps that block.
     */
    private fun shouldPauseForPayment(packageName: String): Boolean {
        if (!PaymentApps.isPaymentApp(packageName)) return false
        // A hard-locked session (e.g. a child's phone time) must not be escapable by
        // opening a payment app, which would switch the whole shield off.
        if (isHardLockHolding()) return false
        if (!PaymentPause.isAutoPauseEnabled(this)) return false
        if (screenLearner?.isActive == true) return false
        val ownRuleBlocksNow = blockingEngine?.evaluate(packageName)?.shouldBlock == true
        return !ownRuleBlocksNow
    }

    /**
     * Switches this service off so a payment app can run, leaving a reminder to turn it
     * back on. [disableSelf] is the only way an app may turn its own accessibility
     * service off; turning it on again is always the user's decision.
     *
     * Strict Mode does not prevent this: being unable to pay is never an acceptable cost
     * of a focus session.
     */
    private fun pauseForPayment(triggerPackage: String?) {
        if (isPausingForPayment) return
        if (isHardLockHolding()) {
            showToast("Payment Pause is off until this locked session ends.", long = false)
            return
        }
        isPausingForPayment = true

        val now = System.currentTimeMillis()
        flushForegroundTime(now)
        flushContentTime(now)
        usageTicker?.cancel()
        usageTicker = null

        val label = triggerPackage?.let { PaymentApps.labelFor(this, it) }
        Log.i(TAG, "Payment Pause for ${triggerPackage ?: "manual request"}")
        PaymentPause.onPaused(this, label)
        showToast(
            "UltimateFocus paused so " + (label ?: "your payment app") +
                " can work. Tap the notification to turn it back on."
        )
        disableSelf()
    }

    /** True while a hard-locked custom session is running. */
    private fun isHardLockHolding(): Boolean =
        repository?.activeSession?.value?.let { it.isRunning && it.hardLock } == true

    /**
     * Records screen signatures while a teach session is running.
     *
     * Returns true when learning is active, so the caller skips evaluation entirely.
     */
    private fun handleLearning(packageName: String): Boolean {
        val learner = screenLearner ?: return false
        if (!learner.isActive) return false

        if (packageName == learner.activePackage()) {
            learner.record(packageName, NodeScanner.collectViewIds(rootInActiveWindow))
        }

        learner.finishIfDue()?.let { result ->
            if (result.signals.isEmpty()) {
                Log.w(TAG, "Teach session captured nothing for " + result.packageName)
                showToast("Nothing unique found. Open the app first, then navigate to the screen while the timer runs.")
            } else {
                Log.i(TAG, "Learned " + result.signals.size + " signals: " + result.signals)
                repository?.applyLearnedSignals(result.ruleId, result.signals)
                showToast("Learned this screen (" + result.signals.size + " signals). It is blocked now.")
            }
        }
        return true
    }

    private fun showToast(message: String, long: Boolean = true) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                message,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Asks the matching detector whether any of [targets] is on screen.
     *
     * Returns null when the app has no detector, which the engine reads as "no content
     * verdict available" rather than "nothing detected". Detection is also where
     * per-content viewing time is banked, because this is the only place that knows
     * which surface is actually being looked at.
     */
    private fun detectContent(
        packageName: String,
        targets: List<ContentTarget>
    ): ContentDetectionResult? {
        // Scanning is generic: it looks for the ids and tabs the caller asked about.
        // Gating it on a per-app detector class meant a screen the user taught on their
        // own device could never match unless that app happened to be in the catalogue,
        // which defeated the point of teaching one.
        val result = try {
            detectionEngine.detect(rootInActiveWindow, targets)
        } catch (e: Exception) {
            Log.w(TAG, "Content detection failed for $packageName", e)
            return null
        }

        val now = System.currentTimeMillis()
        trackContentTime(packageName, result, now)

        // A rule is watching this app but nothing matched. Report what the screen does
        // expose, so the catalogue can be corrected from real data rather than guesswork.
        if (!result.detected &&
            targets.isNotEmpty() &&
            now - lastProbeAtMillis > PROBE_INTERVAL_MILLIS
        ) {
            lastProbeAtMillis = now
            Log.i(
                PROBE_TAG,
                packageName + " no match for [" + targets.joinToString(",") { it.id } +
                    "] :: " + NodeScanner.describeScreen(rootInActiveWindow)
            )
        }

        return result
    }

    /**
     * Accumulates time spent inside a specific content surface.
     *
     * Kept separate from whole-app foreground time so a "30 minutes of Reels" budget
     * measures Reels, not Instagram as a whole.
     */
    private fun trackContentTime(
        packageName: String,
        result: ContentDetectionResult,
        nowMillis: Long
    ) {
        val key = if (result.detected && result.contentId != null) {
            ContentCatalog.usageKey(packageName, result.contentId)
        } else {
            null
        }

        if (key == currentContentKey) {
            // Still on the same surface: bank the slice since the last check.
            if (key != null) {
                val elapsed = nowMillis - contentSinceMillis
                if (elapsed > 0L) {
                    repository?.addForegroundTime(key, elapsed)
                    contentSinceMillis = nowMillis
                }
            }
            return
        }

        flushContentTime(nowMillis)
        currentContentKey = key
        contentSinceMillis = nowMillis
    }

    private fun flushContentTime(nowMillis: Long) {
        val key = currentContentKey ?: return
        val elapsed = nowMillis - contentSinceMillis
        if (elapsed > 0L) repository?.addForegroundTime(key, elapsed)
        currentContentKey = null
    }

    /**
     * Reads the package that owns the active window.
     *
     * Needed because window-change events only tell us about transitions. When the
     * service starts (first enable, app reinstall, system restart) the user is usually
     * already inside some app, and without this the service would not know which app
     * that is until they next switched — so usage would stop accruing and a daily
     * allowance would never fire.
     */
    private fun resolveActiveWindowPackage(): String? = try {
        rootInActiveWindow?.packageName?.toString()
    } catch (e: Exception) {
        Log.w(TAG, "Could not read active window", e)
        null
    }

    /** Adopts whatever app is on screen right now as the current foreground app. */
    private fun seedForegroundFromActiveWindow() {
        val current = resolveActiveWindowPackage() ?: return
        foregroundPackage = current
        foregroundSinceMillis = System.currentTimeMillis()
        Log.d(TAG, "Seeded foreground package as $current")
    }

    /**
     * Re-checks the app the user is sitting in. Without this, an allowance would only be
     * enforced at the moment they switched apps, which could be long past the limit.
     */
    private fun startUsageTicker() {
        usageTicker?.cancel()
        usageTicker = serviceScope.launch {
            while (true) {
                delay(USAGE_TICK_MILLIS)
                val now = System.currentTimeMillis()

                // A teach session must end on time even if the app has gone quiet.
                screenLearner?.let { learner ->
                    if (learner.isActive) {
                        learner.activePackage()?.let { target ->
                            if (foregroundPackage == target) {
                                learner.record(target, NodeScanner.collectViewIds(rootInActiveWindow))
                            }
                        }
                        learner.finishIfDue()?.let { result ->
                            if (result.signals.isNotEmpty()) {
                                repository?.applyLearnedSignals(result.ruleId, result.signals)
                                showToast(
                                    "Learned this screen (" + result.signals.size +
                                        " signals). It is blocked now."
                                )
                            } else {
                                showToast("Nothing unique found. Open the app first, then navigate to the screen while the timer runs.")
                            }
                        }
                        continue
                    }
                }

                // Fall back to the active window if no transition has been seen yet.
                val current = foregroundPackage ?: resolveActiveWindowPackage()?.also {
                    foregroundPackage = it
                    foregroundSinceMillis = now
                } ?: continue

                flushForegroundTime(now)
                val tickSuppressed = suppressor?.isSuppressed(current) == true
                val decision = blockingEngine?.evaluate(current, now) { targets ->
                    detectContent(current, targets)
                } ?: continue
                if (!decision.shouldBlock && tickSuppressed) {
                    suppressor?.clear()
                }
                if (tickSuppressed) continue
                Log.d(TAG, "Tick $current -> ${decision.reason}")
                if (decision.shouldBlock) {
                    handleBlock(current, decision)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "FocusAccessibilityService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "UltimateFocus accessibility service disconnected")
        PaymentPause.pauseHandler = null
        PaymentPause.refreshTile(this)
        val now = System.currentTimeMillis()
        flushForegroundTime(now)
        flushContentTime(now)
        lastContentBlockAt.clear()
        lastPlainBlockAt.clear()
        usageTicker?.cancel()
        usageTicker = null
        stopContentWatch()
        foregroundPackage = null
        lastHandledPackage = null
        return super.onUnbind(intent)
    }

    private companion object {
        const val TAG = "FocusGuardA11y"

        /** Diagnostic tag: `adb logcat -s FocusGuardProbe:*` shows unmatched screens. */
        const val PROBE_TAG = "FocusGuardProbe"
        const val PROBE_INTERVAL_MILLIS = 4000L

        /**
         * Android emits several window-state events per app launch. Without a cooldown
         * a single launch would stack multiple interventions and log duplicate attempts.
         */
        const val INTERVENTION_COOLDOWN_MILLIS = 3000L

        /** How often an app the user is sitting in is re-checked against its allowance. */
        const val USAGE_TICK_MILLIS = 15_000L

        /** Minimum gap between node-tree scans while a feed is scrolling. */
        const val CONTENT_SCAN_THROTTLE_MILLIS = 700L

        /** First re-check after opening an app with a part-of-app block. */
        const val CONTENT_WATCH_FIRST_MILLIS = 600L

        /** Re-check interval while the user stays in that app. */
        const val CONTENT_WATCH_INTERVAL_MILLIS = 1_500L


        /** Time for the global BACK action to take effect before showing the screen. */
        const val BACK_SETTLE_DELAY_MILLIS = 400L

        /** A plain block can fire again this soon. */
        const val PLAIN_COOLDOWN_MILLIS = 700L

        /** Quiet time after a plain block while BACK takes effect. */
        const val PLAIN_SUPPRESS_MILLIS = 900L

        /** Blocked again within this long after BACK means BACK did not leave the feed. */
        const val PLAIN_LOOP_WINDOW_MILLIS = 2_500L

        /** At most one "blocked" message this often. */
        const val PLAIN_TOAST_GAP_MILLIS = 4_000L

        /** How long after a block a floating player from that app is treated as its leftover. */
        const val PIP_FOLLOW_UP_MILLIS = 15_000L

        /** Extra picture-in-picture checks after a block, measured from the previous one. */
        val PIP_CHECK_DELAYS_MILLIS = listOf(600L, 900L, 1_500L, 3_000L)
    }
}
