package ua.alert.wear.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ua.alert.shared.AlertEvent
import ua.alert.shared.ThreatType
import ua.alert.wear.service.AlertAlarmService

class FullScreenAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFullScreenWakeUp()

        setContent {
            val currentAlert by AlertAlarmService.activeAlert.collectAsState()
            val isRinging by AlertAlarmService.isRinging.collectAsState()

            // If an alert was passed via intent extras, use it as fallback
            val fallbackEvent = intent.getStringExtra(AlertAlarmService.EXTRA_ALERT_JSON)?.let {
                try { AlertEvent.fromJson(it) } catch (e: Exception) { null }
            }

            val displayAlert = currentAlert ?: fallbackEvent

            if (displayAlert != null) {
                if (displayAlert.threatType == ThreatType.ALL_CLEAR || !displayAlert.isActive) {
                    AllClearScreen(
                        alert = displayAlert,
                        onClose = {
                            finish()
                        }
                    )
                } else {
                    AlertScreen(
                        alert = displayAlert,
                        onDismiss = {
                            AlertAlarmService.stopRingingFromWatch(this@FullScreenAlertActivity)
                            finish()
                        }
                    )
                }
            } else {
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
