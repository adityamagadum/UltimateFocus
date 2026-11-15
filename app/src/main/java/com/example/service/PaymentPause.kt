package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Payment Pause: switches the shield off so payment and banking apps keep working.
 *
 * Why this exists: an accessibility service that was not installed from Google Play is
 * reported by Play Integrity as UNKNOWN_CONTROLLING, and Google Pay, PhonePe and most
 * Indian banks refuse to run while one is on. No code can change that verdict for a
 * sideloaded build, and switching the service on and off is the user's decision alone,
 * except for [android.accessibilityservice.AccessibilityService.disableSelf], which a
 * service may call on itself.
 *
 * So the service turns itself off when a payment app opens (or when the user taps the
 * Quick Settings tile), and leaves a notification that takes the user straight back to
 * switch it on again once they have paid. When UltimateFocus is installed from Google
 * Play the verdict becomes KNOWN_CONTROLLING, which these apps accept, and the pause is
 * rarely needed; it stays as a safety net for banks that also reject Play-installed apps.
 */
object PaymentPause {

    private const val TAG = "FocusGuardPayPause"
    private const val PREFS = "payment_pause"
    private const val KEY_AUTO = "auto_pause_enabled"
    private const val KEY_PAUSED_AT = "paused_at"
    private const val KEY_PAUSED_FOR = "paused_for"

    const val CHANNEL_ID = "payment_pause"
    const val NOTIFICATION_ID = 4201

    /** Set on the MainActivity intent to open the shield's switch in system Settings. */
    const val EXTRA_RESUME_SHIELD = "com.example.extra.RESUME_SHIELD"

    /**
     * Supplied by the running service so the Quick Settings tile can pause it. Null
     * whenever the service is not connected.
     */
    @Volatile
    internal var pauseHandler: ((triggerPackage: String?) -> Unit)? = null

    /** Whether opening a payment app pauses the shield automatically. On by default. */
    fun isAutoPauseEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoPauseEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    /** When the shield was last paused for a payment, or 0 if it is not paused. */
    fun pausedAtMillis(context: Context): Long = prefs(context).getLong(KEY_PAUSED_AT, 0L)

    /** Asks the running service to pause. Returns false if the service is not running. */
    fun requestPause(triggerPackage: String? = null): Boolean {
        val handler = pauseHandler ?: return false
        handler(triggerPackage)
        return true
    }

    /** Called by the service just before it switches itself off. */
    internal fun onPaused(context: Context, appLabel: String?) {
        prefs(context).edit()
            .putLong(KEY_PAUSED_AT, System.currentTimeMillis())
            .putString(KEY_PAUSED_FOR, appLabel)
            .apply()
        showResumeNotification(context, appLabel)
        refreshTile(context)
    }

    /** Called by the service when the user has switched it back on. */
    internal fun onResumed(context: Context) {
        prefs(context).edit().remove(KEY_PAUSED_AT).remove(KEY_PAUSED_FOR).apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        refreshTile(context)
    }

    /** Intent that opens UltimateFocus and sends the user to the shield's switch. */
    fun resumeIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_RESUME_SHIELD, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun resumePendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            resumeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun showResumeNotification(context: Context, appLabel: String?) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Without the permission the reminder cannot be shown. The user still gets
            // the toast, and opening UltimateFocus asks them to switch the shield on.
            Log.w(TAG, "Notification permission missing; resume reminder not shown")
            return
        }

        val reason = if (appLabel != null) "so $appLabel can work" else "for your payment"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Shield paused $reason")
            .setContentText("Done paying? Tap to turn UltimateFocus back on.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Payment and banking apps do not work while UltimateFocus is on, so it " +
                        "switched itself off. Done paying? Tap here, then turn UltimateFocus on."
                )
            )
            .setContentIntent(resumePendingIntent(context))
            .setOngoing(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post resume reminder", e)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Payment Pause",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to turn the shield back on after paying."
            }
        )
    }

    /** Asks the system to redraw the Quick Settings tile with the new state. */
    fun refreshTile(context: Context) {
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, PaymentPauseTileService::class.java)
            )
        } catch (e: Exception) {
            Log.d(TAG, "Tile refresh skipped", e)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
