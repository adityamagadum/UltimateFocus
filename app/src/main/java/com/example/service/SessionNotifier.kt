package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.FocusGuardApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.PersistedSession
import com.example.data.model.SessionProfile
import com.example.data.repository.FocusGuardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Shows the running session in the notification shade: its name, a live countdown,
 * a progress bar, the end time, and Pause/Resume and End buttons.
 *
 * Built to cost nothing while it runs:
 *  - The seconds countdown is a system chronometer. Android ticks it itself, so the app
 *    posts nothing per second.
 *  - The notification is only rebuilt when the session changes (start, pause, resume,
 *    end) and once a minute for the progress bar and "minutes left" text.
 *  - Android removes it by itself when the timer ends (Android 8+), even if the app
 *    is not running at that moment.
 *
 * During a hard-locked session, or Strict Mode, the buttons are left out: the
 * notification must not become a way around the lock.
 */
class SessionNotifier(
    private val context: Context,
    private val repository: FocusGuardRepository,
    private val scope: CoroutineScope
) {
    private var minuteTicker: Job? = null

    fun start() {
        scope.launch {
            combine(
                repository.persistedSession,
                repository.isStrictModeEnabled,
                repository.sessionProfiles
            ) { session, strict, profiles -> Triple(session, strict, profiles) }
                .distinctUntilChanged()
                .collect { (session, strict, profiles) -> render(session, strict, profiles) }
        }
    }

    private fun render(session: PersistedSession?, strict: Boolean, profiles: List<SessionProfile>) {
        minuteTicker?.cancel()
        minuteTicker = null
        if (session == null || session.remainingSecondsAt(System.currentTimeMillis()) <= 0) {
            cancel()
            return
        }
        post(session, strict, profiles)
        if (!session.isPaused) {
            minuteTicker = scope.launch {
                while (isActive) {
                    delay(MINUTE_MILLIS)
                    if (session.remainingSecondsAt(System.currentTimeMillis()) <= 0) {
                        cancel()
                        break
                    }
                    post(session, strict, profiles)
                }
            }
        }
    }

    private fun post(session: PersistedSession, strict: Boolean, profiles: List<SessionProfile>) {
        if (!canNotify()) return
        ensureChannel()

        val now = System.currentTimeMillis()
        val remainingSeconds = session.remainingSecondsAt(now)
        val elapsedSeconds = (session.totalDurationSeconds - remainingSeconds).coerceAtLeast(0)
        val locked = session.hardLock || (strict && !session.isPaused)
        val emoji = session.profileId?.let { id -> profiles.firstOrNull { it.id == id }?.emoji }
        val title = listOfNotNull(emoji, session.title).joinToString(" ")
        val left = formatLeft(remainingSeconds)
        val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.endsAtEpochMillis))

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setProgress(session.totalDurationSeconds.coerceAtLeast(1), elapsedSeconds, false)

        if (session.isPaused) {
            builder
                .setShowWhen(false)
                .setUsesChronometer(false)
                .setContentText("Paused · $left left")
        } else {
            builder
                // The live seconds countdown, ticked by the system.
                .setShowWhen(true)
                .setWhen(session.endsAtEpochMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentText("$left left · ends $endsAt")
                // Removed automatically when the time is up.
                .setTimeoutAfter(remainingSeconds * 1000L)
        }

        when {
            session.hardLock -> builder.setSubText("🔒 Hard lock")
            locked -> builder.setSubText("Strict Mode")
        }

        if (!locked) {
            builder.addAction(
                0,
                if (session.isPaused) "Resume" else "Pause",
                actionIntent(ACTION_TOGGLE_PAUSE)
            )
            builder.addAction(0, "End", actionIntent(ACTION_END))
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post session notification", e)
        }
    }

    private fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Session timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Time left in a running session, with pause and end buttons."
                setShowBadge(false)
            }
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, SessionActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val TAG = "SessionNotifier"
        const val CHANNEL_ID = "session_timer"
        const val NOTIFICATION_ID = 4210
        const val ACTION_TOGGLE_PAUSE = "com.example.session.TOGGLE_PAUSE"
        const val ACTION_END = "com.example.session.END"
        private const val MINUTE_MILLIS = 60_000L

        internal fun formatLeft(seconds: Int): String {
            val minutes = (seconds + 59) / 60 // round up, so "1 min" shows until the end
            return when {
                minutes < 60 -> "$minutes min"
                minutes % 60 == 0 -> "${minutes / 60} h"
                else -> "${minutes / 60} h ${minutes % 60} min"
            }
        }
    }
}

/**
 * Handles the notification's Pause/Resume and End buttons. The repository still
 * enforces every lock, so a stale button can never end a hard-locked session.
 */
class SessionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = FocusGuardApplication.container() ?: return
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.repository.awaitReady()
                when (intent.action) {
                    SessionNotifier.ACTION_TOGGLE_PAUSE -> container.repository.togglePauseSession()
                    SessionNotifier.ACTION_END -> container.repository.endActiveSession()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
