package com.example.service

import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile for Payment Pause.
 *
 * Shield on: tapping pauses it, for a payment app the automatic pause did not recognise.
 * Shield off: tapping opens the switch to turn it back on.
 */
class PaymentPauseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val isOn = AccessibilityPermission.isServiceEnabled(this)
        if (isOn && PaymentPause.requestPause(null)) {
            render(forceOff = true)
            return
        }
        openResumeScreen()
    }

    private fun render(forceOff: Boolean = false) {
        val tile = qsTile ?: return
        val isOn = !forceOff && AccessibilityPermission.isServiceEnabled(this)
        tile.label = "Payment Pause"
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isOn) "Shield on" else "Shield paused"
        }
        tile.contentDescription = if (isOn) {
            "UltimateFocus shield is on. Tap to pause it for a payment."
        } else {
            "UltimateFocus shield is paused. Tap to turn it back on."
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openResumeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PaymentPause.resumePendingIntent(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(PaymentPause.resumeIntent(this))
        }
    }
}
