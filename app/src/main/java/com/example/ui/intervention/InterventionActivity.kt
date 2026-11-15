package com.example.ui.intervention

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.FocusGuardApplication
import com.example.data.model.BlockMode
import com.example.data.model.InterventionConfig
import com.example.service.BlockSuppressor
import com.example.ui.pause.PausePreview
import com.example.ui.theme.FocusGuardTheme

/**
 * Full-screen pause shown over a blocked app.
 *
 * The accessibility service cannot draw UI itself, so it launches this Activity with the
 * details of the block. It reads state straight from the shared repository rather than
 * through a ViewModel, so opening it never triggers unrelated work such as listing apps.
 */
class InterventionActivity : ComponentActivity() {

    /** Bumped when a new block arrives while this screen is already showing. */
    private var intentVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The hardware/gesture back gesture must leave the same way the button does,
        // otherwise finishing this task drops the user on the launcher.
        onBackPressedDispatcher.addCallback(this) { dismiss() }

        val repository = FocusGuardApplication.container()?.repository
        setContent {
            FocusGuardTheme {
                val savedConfig = repository?.interventionConfig?.collectAsState()?.value
                    ?: InterventionConfig()
                // A preview opened from the editor shows the unsaved draft.
                val draft = if (isPreview) PausePreview.design else null
                val config = if (draft != null) savedConfig.copy(design = draft) else savedConfig
                val stats = repository?.dailyStats?.collectAsState()?.value
                val session = repository?.activeSession?.collectAsState()?.value
                // Read so a block arriving through onNewIntent redraws the screen.
                @Suppress("UNUSED_VARIABLE") val version = intentVersion
                val block = blockInfo()
                InterventionScreen(
                    block = block,
                    config = config,
                    goal = stats?.todayGoalText.orEmpty(),
                    sessionTimeLeft = session
                        ?.takeIf { it.isRunning && !it.isPaused }
                        ?.formattedRemainingTime,
                    closeLabel = when {
                        isPreview -> "Close preview"
                        block.blockMode == BlockMode.CONTENT_LEVEL && !isRepeatBlock ->
                            "Back to " + block.appName
                        else -> "Close " + block.appName
                    },
                    onClose = { dismiss() }
                )
            }
        }
    }

    private val isPreview: Boolean
        get() = intent?.getBooleanExtra(EXTRA_PREVIEW, false) ?: false

    private val isRepeatBlock: Boolean
        get() = intent?.getBooleanExtra(EXTRA_IS_REPEAT_BLOCK, false) ?: false

    private fun blockInfo(): BlockInfo {
        val mode = intent?.getStringExtra(EXTRA_BLOCK_MODE)
            ?.let { name -> BlockMode.entries.firstOrNull { it.name == name } }
            ?: if (intent?.getBooleanExtra(EXTRA_IS_CONTENT_BLOCK, false) == true) {
                BlockMode.CONTENT_LEVEL
            } else {
                BlockMode.HARD_BLOCK
            }
        return BlockInfo(
            appName = intent?.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: "This app",
            packageName = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty(),
            blockMode = mode,
            ruleLabel = intent?.getStringExtra(EXTRA_RULE_LABEL).orEmpty()
        )
    }

    /** launchMode is singleTask, so a repeat block arrives here rather than in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentVersion++
    }

    /**
     * Leaves the intervention.
     *
     * For a content-level block the service has already steered the app out of the
     * short-form feed, so the user is returned to that app — blocking Reels should not
     * cost them the rest of the app. This Activity runs in its own task, so simply
     * finishing would drop them on the launcher; the app's own task has to be brought
     * back to the front explicitly.
     *
     * A whole-app block has nowhere safe to return to, so it goes to the launcher.
     */
    private fun dismiss() {
        if (isPreview) {
            finish()
            return
        }
        val isContentBlock = intent?.getBooleanExtra(EXTRA_IS_CONTENT_BLOCK, false) ?: false
        val isRepeatBlock = intent?.getBooleanExtra(EXTRA_IS_REPEAT_BLOCK, false) ?: false
        val blockedPackage = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)

        // The grace period starts now, as the user returns, rather than when the block
        // fired: someone reading the intervention easily outlasts a few seconds, and an
        // expired window would re-block them the instant they land back in the app.
        if (!blockedPackage.isNullOrBlank()) {
            val duration = if (isRepeatBlock) {
                BlockSuppressor.LOOP_ESCAPE_MILLIS
            } else {
                BlockSuppressor.RETURN_GRACE_MILLIS
            }
            FocusGuardApplication.container()?.blockSuppressor?.suppress(blockedPackage, duration)
        }

        // Returning into the app is only safe the first time. On a repeat the app has
        // already proven it restores the blocked surface, so going back would simply
        // start the loop again; the launcher is the reliable exit.
        if (isContentBlock && !isRepeatBlock && !blockedPackage.isNullOrBlank()) {
            if (returnToApp(blockedPackage)) {
                finish()
                return
            }
        }

        goHome()
        finish()
    }

    /**
     * Sends the user back to the blocked app's *home* screen.
     *
     * Resuming the existing task simply restored the screen that was just blocked —
     * the reel, the story — so dismissing the intervention put the user straight back
     * where they started. CLEAR_TASK drops that stack and launches the app fresh, which
     * is its home screen, keeping the rest of the app available without reopening the
     * feed. Returns false if the app cannot be launched.
     */
    private fun returnToApp(blockedPackage: String): Boolean = try {
        val launchIntent = packageManager.getLaunchIntentForPackage(blockedPackage)
        if (launchIntent == null) {
            false
        } else {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            startActivity(launchIntent)
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not return to $blockedPackage", e)
        false
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    companion object {
        private const val TAG = "FocusGuardIntervention"

        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_BLOCKED_APP_NAME = "blocked_app_name"
        const val EXTRA_IS_CONTENT_BLOCK = "is_content_block"
        const val EXTRA_IS_REPEAT_BLOCK = "is_repeat_block"
        const val EXTRA_BLOCK_MODE = "block_mode"
        const val EXTRA_RULE_LABEL = "rule_label"
        const val EXTRA_PREVIEW = "preview"

        /** Shows the pause screen as it would look, without touching any app. */
        fun previewIntent(context: Context): Intent =
            Intent(context, InterventionActivity::class.java).apply {
                putExtra(EXTRA_PREVIEW, true)
                putExtra(EXTRA_BLOCKED_PACKAGE, "com.google.android.youtube")
                putExtra(EXTRA_BLOCKED_APP_NAME, "YouTube")
                putExtra(EXTRA_BLOCK_MODE, BlockMode.CONTENT_LEVEL.name)
                putExtra(EXTRA_RULE_LABEL, "Shorts")
            }

        fun intent(
            context: Context,
            blockedPackage: String,
            blockedAppName: String,
            blockMode: BlockMode,
            ruleLabel: String,
            isContentBlock: Boolean = false,
            isRepeatBlock: Boolean = false
        ): Intent =
            Intent(context, InterventionActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_PACKAGE, blockedPackage)
                putExtra(EXTRA_BLOCKED_APP_NAME, blockedAppName)
                putExtra(EXTRA_BLOCK_MODE, blockMode.name)
                putExtra(EXTRA_RULE_LABEL, ruleLabel)
                putExtra(EXTRA_IS_CONTENT_BLOCK, isContentBlock)
                putExtra(EXTRA_IS_REPEAT_BLOCK, isRepeatBlock)
                // NEW_TASK is required when starting from a Service context;
                // CLEAR_TOP avoids stacking a second intervention over an existing one.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
