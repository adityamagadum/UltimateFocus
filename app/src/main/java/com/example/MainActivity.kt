package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.AccessibilityPermission
import com.example.service.PaymentPause
import com.example.ui.navigation.FocusGuardApp
import com.example.ui.onboarding.PermissionOnboardingScreen
import com.example.ui.theme.FocusGuardTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Only on a fresh launch: after rotation the user has already been sent to Settings.
    if (savedInstanceState == null) handleResumeShieldRequest(intent)
    setContent {
      FocusGuardTheme {
        val context = LocalContext.current

        // Permission state is read from the system on every resume rather than cached.
        // The user leaves to grant it in system Settings and comes straight back, so a
        // value captured once would be wrong exactly when it matters.
        var isAccessibilityGranted by remember {
          mutableStateOf(AccessibilityPermission.isServiceEnabled(context))
        }

        // Set when the user chooses to look around without granting. Deliberately not
        // persisted: if the permission is still missing next launch they are asked
        // again, while granting it means this screen is never shown again.
        var skippedThisSession by rememberSaveable { mutableStateOf(false) }

        // Once the main app is on screen it stays there. If the shield is switched off
        // later (Payment Pause, "Pause the shield now" in Settings), the app used to swap
        // to the permission screen on return and throw away where the user was. Home
        // already shows a "Blocking is off" banner with a button to fix it.
        var hasEnteredApp by rememberSaveable { mutableStateOf(false) }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
              isAccessibilityGranted = AccessibilityPermission.isServiceEnabled(context)
            }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Payment Pause reminds the user to switch the shield back on with a
        // notification, which needs this permission on Android 13 and newer. Asked
        // once the shield is on, so the first screen stays a single clear request.
        val notificationPermission = rememberLauncherForActivityResult(
          ActivityResultContracts.RequestPermission()
        ) { }
        LaunchedEffect(isAccessibilityGranted) {
          if (isAccessibilityGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
          ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }

        if (isAccessibilityGranted || skippedThisSession || hasEnteredApp) {
          LaunchedEffect(Unit) { hasEnteredApp = true }
          FocusGuardApp(
            // moveTaskToBack leaves the app running so a capture window keeps going.
            onMinimise = { moveTaskToBack(true) }
          )
        } else {
          PermissionOnboardingScreen(
            isAccessibilityGranted = isAccessibilityGranted,
            onGrantAccessibility = { AccessibilityPermission.openServiceSwitch(context) },
            onContinueAnyway = { skippedThisSession = true }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleResumeShieldRequest(intent)
  }

  /**
   * The Payment Pause notification and tile open the app with [PaymentPause.EXTRA_RESUME_SHIELD]
   * so the user lands on the shield's own switch instead of hunting for it in Settings.
   */
  private fun handleResumeShieldRequest(intent: Intent?) {
    if (intent == null || !intent.getBooleanExtra(PaymentPause.EXTRA_RESUME_SHIELD, false)) return
    intent.removeExtra(PaymentPause.EXTRA_RESUME_SHIELD)
    if (!AccessibilityPermission.isServiceEnabled(this)) {
      AccessibilityPermission.openServiceSwitch(this)
    }
  }
}
