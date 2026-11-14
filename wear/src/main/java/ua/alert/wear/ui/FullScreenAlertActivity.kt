package ua.alert.wear.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ua.alert.shared.AlertEvent
import ua.alert.shared.ThreatType
import ua.alert.wear.service.AlertAlarmService

class FullScreenAlertActivity : ComponentActivity() {

    private var latestIntent by mutableStateOf<Intent?>(null)
    private var dismissHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestIntent = intent
        dismissHandled = false

        setupFullScreenWakeUp()

        onBackPressedDispatcher.addCallback(this) {
            dismissAlertAndFinish()
        }

        setContent {
            val currentAlert by AlertAlarmService.activeAlert.collectAsState()

            LaunchedEffect(Unit) {
                AlertAlarmService.closeFullScreenSignal.collect {
                    dismissHandled = true
                    if (!isFinishing) {
                        finish()
                    }
                }
            }

            val fallbackEvent = latestIntent?.getStringExtra(AlertAlarmService.EXTRA_ALERT_JSON)?.let {
                try { AlertEvent.fromJson(it) } catch (_: Exception) { null }
            }

            val displayAlert = currentAlert ?: fallbackEvent

            if (displayAlert != null) {
                SwipeLeftOrRightToDismiss(onDismiss = { dismissAlertAndFinish() }) {
                    if (displayAlert.threatType == ThreatType.ALL_CLEAR || !displayAlert.isActive) {
                        AllClearScreen(
                            alert = displayAlert,
                            onDismissNotification = { dismissAlertAndFinish() }
                        )
                    } else {
                        AlertScreen(
                            alert = displayAlert,
                            onDismiss = { dismissAlertAndFinish() }
                        )
                    }
                }
            } else {
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
        dismissHandled = false
    }

    override fun onDestroy() {
        // Ensure system swipe-to-dismiss or back gesture stops repeating alarm vibration immediately.
        if (isFinishing && !dismissHandled) {
            dismissHandled = true
            AlertAlarmService.dismissNotificationFromWatch(this)
        }
        super.onDestroy()
    }

    private fun dismissAlertAndFinish() {
        if (!dismissHandled) {
            dismissHandled = true
            AlertAlarmService.dismissNotificationFromWatch(this)
        }
        if (!isFinishing) {
            finish()
        }
    }

    private fun setupFullScreenWakeUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
