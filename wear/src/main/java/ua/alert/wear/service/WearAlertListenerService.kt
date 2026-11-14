package ua.alert.wear.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import ua.alert.shared.AlertEvent
import ua.alert.shared.AlertTransitionPolicy
import ua.alert.shared.ProtocolConstants
import ua.alert.shared.ThreatType
import ua.alert.wear.data.AlertHistoryManager
import ua.alert.wear.sensor.WearOffBodyDetector

class WearAlertListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "onMessageReceived: path=${messageEvent.path}")

        when (messageEvent.path) {
            ProtocolConstants.PATH_TRIGGER_ALERT -> {
                val jsonStr = String(messageEvent.data, Charsets.UTF_8)
                try {
                    val event = AlertEvent.fromJson(jsonStr)
                    if (AlertTransitionPolicy.isIgnoredAlarmType(event.alarmType) ||
                        event.threatType == ThreatType.INFO
                    ) {
                        return
                    }

                    val isOnWrist = runBlocking {
                        WearOffBodyDetector(applicationContext).isWatchOnWrist()
                    }
                    if (!isOnWrist) {
                        Log.i(TAG, "Watch is off-wrist; suppressing alert notification & vibration for ${event.title}")
                        AlertAlarmService.updatePassiveStatus(event)
                        AlertHistoryManager.getInstance(applicationContext).addAlert(event)
                        return
                    }

                    Log.i(TAG, "Received alert event: ${event.title} [Danger: ${event.dangerLevel}]")

                    val intent = Intent(this, AlertAlarmService::class.java).apply {
                        putExtra(AlertAlarmService.EXTRA_ALERT_JSON, jsonStr)
                        action = if (event.threatType == ThreatType.ALL_CLEAR || !event.isActive) {
                            AlertAlarmService.ACTION_ALL_CLEAR
                        } else {
                            AlertAlarmService.ACTION_START_ALERT
                        }
                    }
                    startForegroundService(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse AlertEvent from payload: $jsonStr", e)
                }
            }

            ProtocolConstants.PATH_STOP_ALERT -> {
                Log.i(TAG, "Received PATH_STOP_ALERT from phone. Stopping alarm service.")
                val intent = Intent(this, AlertAlarmService::class.java).apply {
                    action = AlertAlarmService.ACTION_STOP_FROM_PHONE
                }
                startService(intent)
            }

            ProtocolConstants.PATH_SYNC_SCHEDULE -> {
                val jsonStr = String(messageEvent.data, Charsets.UTF_8)
                val prefs = getSharedPreferences("wear_schedule_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("synced_schedule_json", jsonStr).apply()
                Log.d(TAG, "Synced schedule saved locally: $jsonStr")
            }

            ProtocolConstants.PATH_SYNC_REGION -> {
                val jsonStr = String(messageEvent.data, Charsets.UTF_8)
                val prefs = getSharedPreferences("wear_schedule_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("synced_region_json", jsonStr)
                    .putBoolean("is_phone_configured", true)
                    .apply()
                Log.d(TAG, "Synced region saved locally: $jsonStr")
            }

            ProtocolConstants.PATH_SYNC_SETUP -> {
                val configured = String(messageEvent.data, Charsets.UTF_8).trim().toBooleanStrictOrNull() ?: false
                val prefs = getSharedPreferences("wear_schedule_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_phone_configured", configured).apply()
                Log.d(TAG, "Synced phone setup state: $configured")
            }

            ProtocolConstants.PATH_SYNC_STATUS -> {
                val jsonStr = String(messageEvent.data, Charsets.UTF_8)
                try {
                    val event = AlertEvent.fromJson(jsonStr)
                    if (AlertTransitionPolicy.isIgnoredAlarmType(event.alarmType) ||
                        event.threatType == ThreatType.INFO
                    ) {
                        return
                    }
                    val prefs = getSharedPreferences("wear_schedule_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_phone_configured", true).apply()
                    AlertAlarmService.updatePassiveStatus(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse passive alert status: $jsonStr", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "WearAlertListenerService"
    }
}
